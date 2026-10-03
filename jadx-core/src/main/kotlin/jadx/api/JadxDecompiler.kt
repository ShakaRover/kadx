package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.api.metadata.annotations.VarNode
import jadx.api.metadata.annotations.VarRef
import jadx.api.plugins.CustomResourcesLoader
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.events.IJadxEvents
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.pass.JadxPass
import jadx.api.plugins.pass.types.JadxAfterLoadPass
import jadx.api.plugins.pass.types.JadxPassType
import jadx.api.utils.tasks.ITaskExecutor
import jadx.core.Jadx
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.SaveCode
import jadx.core.export.ExportGradle
import jadx.core.export.OutDirs
import jadx.core.plugins.JadxPluginManager
import jadx.core.plugins.PluginContext
import jadx.core.plugins.events.JadxEventsImpl
import jadx.core.utils.DecompilerScheduler
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.core.utils.tasks.TaskExecutor
import jadx.core.xmlgen.ResourcesSaver
import jadx.zip.ZipReader
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
import java.util.stream.Collectors

/**
 * jadx 反编译器主入口（公共 API）。
 *
 * 典型用法：
 * ```
 * JadxArgs args = new JadxArgs();
 * args.inputFiles.add(new File("test.apk"));
 * try (JadxDecompiler jadx = new JadxDecompiler(args)) {
 *     jadx.load();
 *     jadx.save();
 * }
 * ```
 * 也可以遍历反编译后的类：
 * ```
 * for (JavaClass cls : jadx.getClasses()) {
 *     System.out.println(cls.getCode());
 * }
 * ```
 */
class JadxDecompiler : Closeable {
	private val args: JadxArgs
	private val pluginManager: JadxPluginManager
	private val loadedInputs = ArrayList<ICodeLoader>()
	private val zipReader: ZipReader

	private var root: RootNode? = null
	private var classes: List<JavaClass>? = null
	private var resources: List<ResourceFile>? = null

	private val decompileScheduler: IDecompileScheduler = DecompilerScheduler()
	private val resourcesLoader: ResourcesLoader

	private val customCodeLoaders = ArrayList<ICodeLoader>()
	private val customResourcesLoaders = ArrayList<CustomResourcesLoader>()
	private val customPasses = HashMap<JadxPassType, MutableList<JadxPass>>()
	private val closeableList = ArrayList<Closeable>()

	private var eventsImpl: IJadxEvents = JadxEventsImpl()

	constructor() : this(JadxArgs())

	constructor(args: JadxArgs) {
		this.args = checkNotNull(args)
		this.pluginManager = JadxPluginManager(this)
		this.resourcesLoader = ResourcesLoader(this)
		this.zipReader = ZipReader(args.security)
	}

	/** 保存进度回调。 */
	fun interface ProgressListener {
		fun progress(done: Long, total: Long)
	}

	fun load() {
		reset()
		JadxArgsValidator.validate(this)
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
		for (plugin in pluginManager.getResolvedPluginContexts()) {
			for (codeLoader in plugin.getCodeInputs()) {
				try {
					// JadxCodeInput.loadFiles 参数类型是显式 java.util.List（为兼容 Java SAM），此处做一次未检查转型
					@Suppress("UNCHECKED_CAST")
					val loader = codeLoader.loadFiles(inputFiles as java.util.List<Path>)
					if (loader != null && !loader.isEmpty()) {
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
			LOG.debug("Resolved plugins: {}", pluginManager.getResolvedPluginContexts())
		}
		pluginManager.initResolved()
		if (LOG.isDebugEnabled) {
			val passes = customPasses.values.stream().flatMap { it.stream() }
				.map { p -> p.getInfo().getName() }.collect(Collectors.toList())
			LOG.debug("Loaded custom passes: {} {}", passes.size, passes)
		}
	}

	private fun unloadPlugins() {
		pluginManager.unloadResolved()
	}

	private fun loadFinished() {
		LOG.debug("Load finished")
		val list = customPasses[JadxAfterLoadPass.TYPE]
		if (list != null) {
			for (pass in list) {
				(pass as JadxAfterLoadPass).init(this)
			}
		}
	}

	@Suppress("unused")
	fun registerPlugin(plugin: JadxPlugin) {
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
		val rootNode = root ?: throw JadxRuntimeException("No loaded files")
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
		val inputFileNames = args.inputFiles.stream()
			.map { it.absolutePath }
			.collect(Collectors.toSet())
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
			var inputFileName = checkNotNull(cls.getInputFileName())
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
			throw JadxRuntimeException("Decompilation batches build failed", e)
		}
		val decompileTasks = ArrayList<Runnable>(batches.size)
		for (decompileBatch in batches) {
			decompileTasks.add(
				Runnable {
					for (cls in decompileBatch) {
						try {
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
		return rootNode.getErrorsCounter().getErrorCount()
	}

	fun getWarnsCount(): Int {
		val rootNode = root ?: return 0
		return rootNode.getErrorsCounter().getWarnsCount()
	}

	fun printErrorsReport() {
		val rootNode = root ?: return
		checkNotNull(rootNode.getClsp()).printMissingClasses()
		rootNode.getErrorsCounter().printReport()
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
		val clsListNoDup = Utils.collectionMap(pkg.getClassesNoDup()) { cls -> convertClassNode(cls) }
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

	fun searchJavaClassByOrigFullName(fullName: String): JavaClass? = checkNotNull(getRoot()).getClasses().stream()
		.filter { cls -> cls.classInfo.fullName == fullName }
		.findFirst()
		.map { cls -> convertClassNode(cls) }
		.orElse(null)

	fun searchClassNodeByOrigFullName(fullName: String): ClassNode? = checkNotNull(getRoot()).getClasses().stream()
		.filter { cls -> cls.classInfo.fullName == fullName }
		.findFirst()
		.orElse(null)

	/**
	 * 如果类带有 DONT_GENERATE 标记，则返回其父类。
	 */
	fun searchJavaClassOrItsParentByOrigFullName(fullName: String): JavaClass? {
		val node = checkNotNull(getRoot()).getClasses().stream()
			.filter { cls -> cls.classInfo.fullName == fullName }
			.findFirst()
			.orElse(null)
		if (node != null) {
			return if (node.contains(AFlag.DONT_GENERATE)) {
				convertClassNode(node.getTopParentClass())
			} else {
				convertClassNode(node)
			}
		}
		return null
	}

	fun searchJavaClassByAliasFullName(fullName: String): JavaClass? = checkNotNull(getRoot()).getClasses().stream()
		.filter { cls -> cls.classInfo.aliasFullName == fullName }
		.findFirst()
		.map { cls -> convertClassNode(cls) }
		.orElse(null)

	fun getJavaNodeByRef(ann: ICodeNodeRef): JavaNode? = getJavaNodeByCodeAnnotation(null, ann)

	fun getJavaNodeByCodeAnnotation(codeInfo: ICodeInfo?, ann: ICodeAnnotation?): JavaNode? {
		if (ann == null) {
			return null
		}
		return when (ann.getAnnType()) {
			ICodeAnnotation.AnnType.CLASS -> convertClassNode(ann as ClassNode)
			ICodeAnnotation.AnnType.METHOD -> convertMethodNode(ann as MethodNode)
			ICodeAnnotation.AnnType.FIELD -> convertFieldNode(ann as FieldNode)
			ICodeAnnotation.AnnType.PKG -> convertPackageNode(ann as PackageNode)
			ICodeAnnotation.AnnType.DECLARATION -> getJavaNodeByCodeAnnotation(codeInfo, (ann as NodeDeclareRef).getNode())
			ICodeAnnotation.AnnType.VAR -> resolveVarNode(ann as VarNode)
			ICodeAnnotation.AnnType.VAR_REF -> resolveVarRef(codeInfo, ann as VarRef)
			ICodeAnnotation.AnnType.OFFSET -> null
			else -> throw JadxRuntimeException("Unknown annotation type: " + ann.getAnnType() + ", class: " + ann.javaClass)
		}
	}

	private fun resolveVarNode(varNode: VarNode): JavaVariable {
		val javaNode = convertMethodNode(varNode.getMth())
		return JavaVariable(javaNode, varNode)
	}

	private fun resolveVarRef(codeInfo: ICodeInfo?, varRef: VarRef): JavaVariable? {
		if (codeInfo == null) {
			throw JadxRuntimeException("Missing code info for resolve VarRef: $varRef")
		}
		val varNodeAnn = codeInfo.getCodeMetadata().getAt(varRef.getRefPos())
		if (varNodeAnn != null && varNodeAnn.getAnnType() == ICodeAnnotation.AnnType.DECLARATION) {
			val nodeRef = (varNodeAnn as NodeDeclareRef).getNode()
			if (nodeRef.getAnnType() == ICodeAnnotation.AnnType.VAR) {
				return resolveVarNode(nodeRef as VarNode)
			}
		}
		return null
	}

	fun convertNodes(nodesList: Collection<ICodeNodeRef>): List<JavaNode> = nodesList.mapNotNull { obj -> getJavaNodeByRef(obj) }

	fun getJavaNodeAtPosition(codeInfo: ICodeInfo, pos: Int): JavaNode? {
		val ann = codeInfo.getCodeMetadata().getAt(pos)
		return getJavaNodeByCodeAnnotation(codeInfo, ann)
	}

	fun getClosestJavaNode(codeInfo: ICodeInfo, pos: Int): JavaNode? {
		val ann = codeInfo.getCodeMetadata().getClosestUp(pos)
		return getJavaNodeByCodeAnnotation(codeInfo, ann)
	}

	fun getEnclosingNode(codeInfo: ICodeInfo, pos: Int): JavaNode? {
		val obj = codeInfo.getCodeMetadata().getNodeAt(pos) ?: return null
		return getJavaNodeByRef(obj)
	}

	fun reloadCodeData() {
		checkNotNull(root).notifyCodeDataListeners()
	}

	fun getArgs(): JadxArgs = args

	fun getPluginManager(): JadxPluginManager = pluginManager

	fun getDecompileScheduler(): IDecompileScheduler = decompileScheduler

	fun events(): IJadxEvents = eventsImpl

	fun setEventsImpl(eventsImpl: IJadxEvents) {
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

	fun addCustomPass(pass: JadxPass) {
		customPasses.computeIfAbsent(pass.getPassType()) { ArrayList() }.add(pass)
	}

	fun getResourcesLoader(): ResourcesLoader = resourcesLoader

	fun getZipReader(): ZipReader = zipReader

	fun addCloseable(closeable: Closeable) {
		closeableList.add(closeable)
	}

	override fun toString(): String = "jadx decompiler " + getVersion()

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxDecompiler::class.java)

		/** 返回 jadx 版本号。 */
		@JvmStatic
		fun getVersion(): String = Jadx.getVersion()
	}
}
