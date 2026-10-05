package kadx.api

import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeNodeRef
import kadx.api.metadata.annotations.NodeDeclareRef
import kadx.api.metadata.annotations.VarNode
import kadx.api.metadata.annotations.VarRef
import kadx.api.plugins.CustomResourcesLoader
import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.events.IKadxEvents
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.pass.types.KadxAfterLoadPass
import kadx.api.plugins.pass.types.KadxPassType
import kadx.api.utils.tasks.ITaskExecutor
import kadx.core.Kadx
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.SaveCode
import kadx.core.export.ExportGradle
import kadx.core.export.OutDirs
import kadx.core.plugins.KadxPluginManager
import kadx.core.plugins.PluginRuntime
import kadx.core.plugins.events.KadxEventsImpl
import kadx.core.utils.DecompilerScheduler
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils
import kadx.core.utils.tasks.TaskExecutor
import kadx.core.xmlgen.ResourcesSaver
import kadx.zip.ZipReader
import org.jetbrains.annotations.ApiStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * kadx 反编译器主入口（公共 API）。
 *
 * 典型用法：
 * ```
 * KadxArgs args = new KadxArgs();
 * args.inputFiles.add(new File("test.apk"));
 * try (KadxDecompiler kadx = new KadxDecompiler(args)) {
 *     kadx.load();
 *     kadx.save();
 * }
 * ```
 * 也可以遍历反编译后的类：
 * ```
 * for (JavaClass cls : kadx.getClasses()) {
 *     System.out.println(cls.getCode());
 * }
 * ```
 */
class KadxDecompiler : Closeable {
	private val args: KadxArgs
	private val pluginManager: KadxPluginManager
	private val loadedInputs = ArrayList<ICodeLoader>()
	private val zipReader: ZipReader

	private var root: RootNode? = null
	private var classes: List<JavaClass>? = null
	private var resources: List<ResourceFile>? = null

	private val decompileScheduler: IDecompileScheduler = DecompilerScheduler()
	private val resourcesLoader: ResourcesLoader

	private val customCodeLoaders = ArrayList<ICodeLoader>()
	private val customResourcesLoaders = ArrayList<CustomResourcesLoader>()
	private val customPasses = HashMap<KadxPassType, MutableList<KadxPass>>()
	private val closeableList = ArrayList<Closeable>()

	private var eventsImpl: IKadxEvents = KadxEventsImpl()

	constructor() : this(KadxArgs())

	constructor(args: KadxArgs) {
		this.args = checkNotNull(args)
		this.pluginManager = KadxPluginManager(args)
		this.resourcesLoader = ResourcesLoader(this)
		this.zipReader = ZipReader(args.security)
	}

	/** 保存进度回调。 */
	fun interface ProgressListener {
		fun progress(done: Long, total: Long)
	}

	fun load() {
		reset()
		KadxArgsValidator.validate(this)
		LOG.info("loading ...")
		FileUtils.updateTempRootDir(args.filesGetter.getTempDir())
		loadPlugins()
		loadInputFiles()

		val rootNode = RootNode(this)
		root = rootNode
		rootNode.init()
		// 加载类与资源
		rootNode.loadClasses(loadedInputs)
		rootNode.loadResources(resourcesLoader, getResources())
		rootNode.finishClassLoad()
		rootNode.initClassPath()
		// 初始化 pass
		rootNode.mergePasses(customPasses)
		rootNode.runPreDecompileStage()
		rootNode.initPasses()
		loadFinished()
	}

	/**
	 * 只重新加载 pass 与插件，不重新处理类与输入。
	 */
	fun reloadPasses() {
		LOG.info("reloading (passes only) ...")
		val rootNode = checkNotNull(root)
		customPasses.clear()
		rootNode.resetPasses()
		eventsImpl.reset()
		unloadPlugins()

		loadPlugins()
		rootNode.mergePasses(customPasses)
		rootNode.restartVisitors()
		rootNode.initPasses()
		loadFinished()
	}

	private fun loadInputFiles() {
		loadedInputs.clear()
		val inputPaths = Utils.collectionMap(args.inputFiles) { it.toPath() }
		val inputFiles = FileUtils.expandDirs(inputPaths)
		val start = System.currentTimeMillis()
		for (plugin in pluginManager.resolvedPlugins) {
			val pluginContext = plugin.pluginContext ?: continue
			for (codeLoader in pluginContext.getCodeInputs()) {
				try {
					// KadxCodeInput.loadFiles 参数类型是显式 java.util.List（为兼容 Java SAM），此处做一次未检查转型
					@Suppress("UNCHECKED_CAST")
					val loader = codeLoader.loadFiles(inputFiles as java.util.List<Path>)
					if (loader != null && !loader.isEmpty) {
						loadedInputs.add(loader)
					}
				} catch (e: Exception) {
					LOG.warn("Failed to load code for plugin: {}", plugin, e)
				}
			}
		}
		loadedInputs.addAll(customCodeLoaders)
		if (LOG.isDebugEnabled) {
			LOG.debug("Loaded using {} inputs plugin in {} ms", loadedInputs.size, System.currentTimeMillis() - start)
		}
	}

	private fun reset() {
		unloadPlugins()
		root = null
		classes = null
		resources = null
		eventsImpl.reset()
	}

	override fun close() {
		reset()
		closeAll(loadedInputs)
		closeAll(customCodeLoaders)
		closeAll(customResourcesLoaders)
		closeAll(closeableList)
		FileUtils.deleteDirIfExists(args.filesGetter.getTempDir())
		args.close()
		FileUtils.clearTempRootDir()
	}

	private fun closeAll(list: MutableList<out Closeable>) {
		try {
			for (closeable in list) {
				try {
					closeable.close()
				} catch (e: Exception) {
					LOG.warn("Fail to close '{}'", closeable, e)
				}
			}
		} finally {
			list.clear()
		}
	}

	private fun loadPlugins() {
		pluginManager.providesSuggestion("java-input", if (args.isUseDxInput) "java-convert" else "java-input")
		pluginManager.load(args.pluginLoader)
		if (LOG.isDebugEnabled) {
			LOG.debug("Resolved plugins: {}", pluginManager.resolvedPlugins)
		}
		pluginManager.initResolved(this)
		if (LOG.isDebugEnabled) {
			val passes = customPasses.values.flatMap { it }
				.map { p -> p.getInfo().getName() }
			LOG.debug("Loaded custom passes: {} {}", passes.size, passes)
		}
	}

	private fun unloadPlugins() {
		pluginManager.unloadResolved()
	}

	private fun loadFinished() {
		LOG.debug("Load finished")
		val list = customPasses[KadxAfterLoadPass.TYPE]
		if (list != null) {
			for (pass in list) {
				(pass as KadxAfterLoadPass).init(this)
			}
		}
	}

	@Suppress("unused")
	fun registerPlugin(plugin: KadxPlugin) {
		pluginManager.register(plugin)
	}

	fun save() {
		save(!args.isSkipSources, !args.isSkipResources)
	}

	fun save(intervalInMillis: Int, listener: ProgressListener) {
		try {
			val tasks = getSaveTaskExecutor()
			tasks.execute()
			val total = tasks.getTasksCount().toLong()
			while (tasks.isRunning()) {
				listener.progress(tasks.getProgress().toLong(), total)
				val executor = tasks.getInternalExecutor() ?: break
				executor.awaitTermination(intervalInMillis.toLong(), TimeUnit.MILLISECONDS)
			}
		} catch (e: InterruptedException) {
			LOG.error("Save interrupted", e)
			Thread.currentThread().interrupt()
		}
	}

	fun saveSources() {
		save(true, false)
	}

	fun saveResources() {
		save(false, true)
	}

	private fun save(saveSources: Boolean, saveResources: Boolean) {
		val executor = getSaveTasks(saveSources, saveResources)
		executor.execute()
		executor.awaitTermination()
	}

	fun getSaveTaskExecutor(): ITaskExecutor = getSaveTasks(!args.isSkipSources, !args.isSkipResources)

	@Deprecated("Use getSaveTaskExecutor() instead.")
	fun getSaveExecutor(): ExecutorService {
		val executor = getSaveTaskExecutor()
		executor.execute()
		return checkNotNull(executor.getInternalExecutor())
	}

	@Deprecated("Use getSaveTaskExecutor() instead.")
	fun getSaveTasks(): List<Runnable> = listOf(Runnable { save() })

	private fun getSaveTasks(saveSources: Boolean, saveResources: Boolean): TaskExecutor {
		val rootNode = root ?: throw KadxRuntimeException("No loaded files")
		val outDirs: OutDirs
		val gradleExport: ExportGradle?
		if (args.exportGradleType != null) {
			gradleExport = ExportGradle(rootNode, checkNotNull(args.outDir), getResources())
			outDirs = gradleExport.init()
		} else {
			gradleExport = null
			outDirs = OutDirs(checkNotNull(args.outDirSrc), checkNotNull(args.outDirRes))
			outDirs.makeDirs()
		}

		val executor = TaskExecutor()
		executor.setThreadsCount(args.threadsCount)
		if (saveResources) {
			// 先保存资源，因为反编译可能中断或失败
			appendResourcesSaveTasks(executor, outDirs.resOutDir)
		}
		if (saveSources) {
			appendSourcesSave(executor, outDirs.srcOutDir)
		}
		if (gradleExport != null) {
			executor.addSequentialTask(Runnable { gradleExport.generateGradleFiles() })
		}
		return executor
	}

	private fun appendResourcesSaveTasks(executor: ITaskExecutor, outDir: File) {
		if (args.isSkipFilesSave) {
			return
		}
		// 先处理 AndroidManifest.xml，以便加载完整的资源 id 表
		for (resourceFile in getResources()) {
			if (resourceFile.getType() == ResourceType.MANIFEST) {
				ResourcesSaver(this, outDir, resourceFile).run()
				break
			}
		}
		val inputFileNames = args.inputFiles.map { it.absolutePath }.toSet()
		val codeSources = collectCodeSources()

		val tasks = ArrayList<Runnable>()
		for (resourceFile in getResources()) {
			val resType = resourceFile.getType()
			if (resType == ResourceType.MANIFEST) {
				// 已经处理过
				continue
			}
			val resOriginalName = resourceFile.getOriginalName()
			if (resType != ResourceType.ARSC && inputFileNames.contains(resOriginalName)) {
				// 忽略由输入文件产生的资源
				continue
			}
			if (codeSources.contains(resOriginalName)) {
				// 不要输出代码源资源（.dex、.class 等）
				// 不要信任文件扩展名，只使用作为类输入的源集合
				continue
			}
			tasks.add(ResourcesSaver(this, outDir, resourceFile))
		}
		executor.addParallelTasks(tasks)
	}

	private fun collectCodeSources(): Set<String> {
		val set = HashSet<String>()
		for (cls in checkNotNull(root).getClasses(true)) {
			if (cls.getClsData() == null) {
				// 排除合成类
				continue
			}
			var inputFileName = checkNotNull(cls.inputFileName)
			if (inputFileName.endsWith(".class")) {
				// 截掉 .class 名称以得到源 .jar 文件
				// 当前模板："<optional input files>:<.jar>:<full class name>"
				val endIdx = inputFileName.lastIndexOf(':')
				if (endIdx != -1) {
					val startIdx = inputFileName.lastIndexOf(':', endIdx - 1) + 1
					inputFileName = inputFileName.substring(startIdx, endIdx)
				}
			}
			set.add(inputFileName)
		}
		return set
	}

	private fun appendSourcesSave(executor: ITaskExecutor, outDir: File) {
		val classes = getClasses()
		val processQueue = filterClasses(classes)
		val batches: List<List<JavaClass>>
		try {
			batches = decompileScheduler.buildBatches(processQueue)
		} catch (e: Exception) {
			throw KadxRuntimeException("Decompilation batches build failed", e)
		}
		val decompileTasks = ArrayList<Runnable>(batches.size)
		for (decompileBatch in batches) {
			decompileTasks.add(
				Runnable {
					for (cls in decompileBatch) {
						try {
							LOG.debug("Decompiling class: {}", cls)
							val clsNode = cls.getClassNode()
							val code = clsNode.getCode()
							SaveCode.save(outDir, clsNode, code)
						} catch (e: Exception) {
							LOG.error("Error saving class: {}", cls, e)
						}
					}
				},
			)
		}
		executor.addParallelTasks(decompileTasks)
	}

	private fun filterClasses(classes: List<JavaClass>): List<JavaClass> {
		val classFilter = args.classFilter
		val list = ArrayList<JavaClass>(classes.size)
		for (cls in classes) {
			val clsNode = cls.getClassNode()
			if (clsNode.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			if (classFilter != null && !classFilter.test(clsNode.classInfo.fullName)) {
				if (!args.isIncludeDependencies) {
					clsNode.add(AFlag.DONT_GENERATE)
				}
				continue
			}
			list.add(cls)
		}
		return list
	}

	@Synchronized
	fun getClasses(): List<JavaClass> {
		val rootNode = root ?: return emptyList()
		var cls = classes
		if (cls == null) {
			val classNodeList = rootNode.getClasses()
			val clsList = ArrayList<JavaClass>(classNodeList.size)
			for (classNode in classNodeList) {
				if (!classNode.contains(AFlag.DONT_GENERATE) && !classNode.isInner()) {
					clsList.add(convertClassNode(classNode))
				}
			}
			cls = Collections.unmodifiableList(clsList)
			classes = cls
		}
		return cls
	}

	fun getClassesWithInners(): List<JavaClass> = Utils.collectionMap(checkNotNull(root).getClasses()) { cls -> convertClassNode(cls) }

	@Synchronized
	fun getResources(): List<ResourceFile> {
		var res = resources
		if (res == null) {
			val rootNode = root ?: return emptyList()
			res = resourcesLoader.load(rootNode)
			resources = res
		}
		return res
	}

	fun getPackages(): List<JavaPackage> = Utils.collectionMap(checkNotNull(root).getPackages()) { pkg -> convertPackageNode(pkg) }

	fun getErrorsCount(): Int {
		val rootNode = root ?: return 0
		return rootNode.errorsCounter.errorCount
	}

	fun getWarnsCount(): Int {
		val rootNode = root ?: return 0
		return rootNode.errorsCounter.getWarnsCount()
	}

	fun printErrorsReport() {
		val rootNode = root ?: return
		checkNotNull(rootNode.getClsp()).printMissingClasses()
		rootNode.errorsCounter.printReport()
	}

	/**
	 * 内部 API，非稳定。
	 */
	@ApiStatus.Internal
	fun getRoot(): RootNode? = root

	/**
	 * 内部 API，非稳定。
	 */
	@ApiStatus.Internal
	@Synchronized
	fun convertClassNode(cls: ClassNode): JavaClass {
		var javaClass = cls.javaNode
		if (javaClass == null) {
			javaClass = if (cls.isInner()) {
				JavaClass(cls, convertClassNode(cls.parentClass))
			} else {
				JavaClass(cls, this)
			}
			cls.javaNode = javaClass
		}
		return javaClass
	}

	@ApiStatus.Internal
	@Synchronized
	fun convertFieldNode(fld: FieldNode): JavaField {
		var javaField = fld.javaNode
		if (javaField == null) {
			val parentCls = convertClassNode(fld.parentClass)
			javaField = JavaField(fld, parentCls)
			fld.javaNode = javaField
		}
		return javaField
	}

	@ApiStatus.Internal
	@Synchronized
	fun convertMethodNode(mth: MethodNode): JavaMethod {
		var javaMethod = mth.javaNode
		if (javaMethod == null) {
			javaMethod = JavaMethod(mth, convertClassNode(mth.parentClass))
			mth.javaNode = javaMethod
		}
		return javaMethod
	}

	@ApiStatus.Internal
	@Synchronized
	fun convertPackageNode(pkg: PackageNode): JavaPackage {
		val foundPkg = pkg.javaNode
		if (foundPkg != null) {
			return foundPkg
		}
		val clsList = Utils.collectionMap(pkg.getClasses()) { cls -> convertClassNode(cls) }
		val clsListNoDup = Utils.collectionMap(pkg.classesNoDup) { cls -> convertClassNode(cls) }
		val subPkgsCount = pkg.getSubPackages().size
		val subPkgs = ArrayList<JavaPackage>(subPkgsCount)
		val javaPkg = JavaPackage(pkg, clsList, clsListNoDup, subPkgs)
		if (subPkgsCount != 0) {
			// 先加父包再加子包，避免无限递归
			for (subPackage in pkg.getSubPackages()) {
				subPkgs.add(convertPackageNode(subPackage))
			}
		}
		pkg.javaNode = javaPkg
		return javaPkg
	}

	fun searchJavaClassByOrigFullName(fullName: String): JavaClass? = checkNotNull(getRoot()).getClasses()
		.firstOrNull { cls -> cls.classInfo.fullName == fullName }
		?.let { cls -> convertClassNode(cls) }

	fun searchClassNodeByOrigFullName(fullName: String): ClassNode? = checkNotNull(getRoot()).getClasses()
		.firstOrNull { cls -> cls.classInfo.fullName == fullName }

	/**
	 * 如果类带有 DONT_GENERATE 标记，则返回其父类。
	 */
	fun searchJavaClassOrItsParentByOrigFullName(fullName: String): JavaClass? {
		val node = checkNotNull(getRoot()).getClasses()
			.firstOrNull { cls -> cls.classInfo.fullName == fullName }
		if (node != null) {
			return if (node.contains(AFlag.DONT_GENERATE)) {
				convertClassNode(node.topParentClass)
			} else {
				convertClassNode(node)
			}
		}
		return null
	}

	fun searchJavaClassByAliasFullName(fullName: String): JavaClass? = checkNotNull(getRoot()).getClasses()
		.firstOrNull { cls -> cls.classInfo.aliasFullName == fullName }
		?.let { cls -> convertClassNode(cls) }

	fun getJavaNodeByRef(ann: ICodeNodeRef): JavaNode? = getJavaNodeByCodeAnnotation(null, ann)

	fun getJavaNodeByCodeAnnotation(codeInfo: ICodeInfo?, ann: ICodeAnnotation?): JavaNode? {
		if (ann == null) {
			return null
		}
		return when (ann.annType) {
			ICodeAnnotation.AnnType.CLASS -> convertClassNode(ann as ClassNode)
			ICodeAnnotation.AnnType.METHOD -> convertMethodNode(ann as MethodNode)
			ICodeAnnotation.AnnType.FIELD -> convertFieldNode(ann as FieldNode)
			ICodeAnnotation.AnnType.PKG -> convertPackageNode(ann as PackageNode)
			ICodeAnnotation.AnnType.DECLARATION -> getJavaNodeByCodeAnnotation(codeInfo, (ann as NodeDeclareRef).getNode())
			ICodeAnnotation.AnnType.VAR -> resolveVarNode(ann as VarNode)
			ICodeAnnotation.AnnType.VAR_REF -> resolveVarRef(codeInfo, ann as VarRef)
			ICodeAnnotation.AnnType.OFFSET -> null
			else -> throw KadxRuntimeException("Unknown annotation type: " + ann.annType + ", class: " + ann.javaClass)
		}
	}

	private fun resolveVarNode(varNode: VarNode): JavaVariable {
		val javaNode = convertMethodNode(varNode.getMth())
		return JavaVariable(javaNode, varNode)
	}

	private fun resolveVarRef(codeInfo: ICodeInfo?, varRef: VarRef): JavaVariable? {
		if (codeInfo == null) {
			throw KadxRuntimeException("Missing code info for resolve VarRef: $varRef")
		}
		val varNodeAnn = codeInfo.codeMetadata.getAt(varRef.getRefPos())
		if (varNodeAnn != null && varNodeAnn.annType == ICodeAnnotation.AnnType.DECLARATION) {
			val nodeRef = (varNodeAnn as NodeDeclareRef).getNode()
			if (nodeRef.annType == ICodeAnnotation.AnnType.VAR) {
				return resolveVarNode(nodeRef as VarNode)
			}
		}
		return null
	}

	fun convertNodes(nodesList: Collection<ICodeNodeRef>): List<JavaNode> = nodesList.mapNotNull { obj -> getJavaNodeByRef(obj) }

	fun getJavaNodeAtPosition(codeInfo: ICodeInfo, pos: Int): JavaNode? {
		val ann = codeInfo.codeMetadata.getAt(pos)
		return getJavaNodeByCodeAnnotation(codeInfo, ann)
	}

	fun getClosestJavaNode(codeInfo: ICodeInfo, pos: Int): JavaNode? {
		val ann = codeInfo.codeMetadata.getClosestUp(pos)
		return getJavaNodeByCodeAnnotation(codeInfo, ann)
	}

	fun getEnclosingNode(codeInfo: ICodeInfo, pos: Int): JavaNode? {
		val obj = codeInfo.codeMetadata.getNodeAt(pos) ?: return null
		return getJavaNodeByRef(obj)
	}

	fun reloadCodeData() {
		checkNotNull(root).notifyCodeDataListeners()
	}

	fun getArgs(): KadxArgs = args

	fun getPluginManager(): KadxPluginManager = pluginManager

	fun getDecompileScheduler(): IDecompileScheduler = decompileScheduler

	fun events(): IKadxEvents = eventsImpl

	fun setEventsImpl(eventsImpl: IKadxEvents) {
		this.eventsImpl = eventsImpl
	}

	fun addCustomCodeLoader(customCodeLoader: ICodeLoader) {
		customCodeLoaders.add(customCodeLoader)
	}

	fun getCustomCodeLoaders(): List<ICodeLoader> = customCodeLoaders

	fun addCustomResourcesLoader(loader: CustomResourcesLoader) {
		if (customResourcesLoaders.contains(loader)) {
			return
		}
		customResourcesLoaders.add(loader)
	}

	fun getCustomResourcesLoaders(): List<CustomResourcesLoader> = customResourcesLoaders

	fun addCustomPass(pass: KadxPass) {
		customPasses.computeIfAbsent(pass.getPassType()) { ArrayList() }.add(pass)
	}

	fun getResourcesLoader(): ResourcesLoader = resourcesLoader

	fun getZipReader(): ZipReader = zipReader

	fun addCloseable(closeable: Closeable) {
		closeableList.add(closeable)
	}

	override fun toString(): String = "kadx decompiler " + getVersion()

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxDecompiler::class.java)

		/** 返回 kadx 版本号。 */
		@JvmStatic
		fun getVersion(): String = Kadx.version
	}
}
