package jadx.gui

import jadx.api.ICodeInfo
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import jadx.api.JavaNode
import jadx.api.JavaPackage
import jadx.api.ResourceFile
import jadx.api.impl.InMemoryCodeCache
import jadx.api.metadata.ICodeNodeRef
import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.SimpleJadxPassInfo
import jadx.api.plugins.pass.types.JadxPreparePass
import jadx.api.usage.impl.EmptyUsageInfoCache
import jadx.api.usage.impl.InMemoryUsageInfoCache
import jadx.cli.JadxAppCommon
import jadx.cli.plugins.JadxFilesGetter
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.ProcessState
import jadx.core.dex.nodes.ProcessState.GENERATED_AND_UNLOADED
import jadx.core.dex.nodes.ProcessState.NOT_LOADED
import jadx.core.dex.nodes.ProcessState.PROCESS_COMPLETE
import jadx.core.dex.nodes.RootNode
import jadx.core.plugins.AppContext
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.cache.code.CodeCacheMode
import jadx.gui.cache.code.CodeStringCache
import jadx.gui.cache.code.disk.BufferCodeCache
import jadx.gui.cache.code.disk.DiskCodeCache
import jadx.gui.cache.usage.UsageCacheMode
import jadx.gui.cache.usage.UsageInfoCache
import jadx.gui.plugins.context.CommonGuiPluginsContext
import jadx.gui.settings.JadxProject
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.utils.CacheObject
import jadx.plugins.tools.JadxExternalPluginsLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections

/**
 * 核心 [JadxDecompiler] 的 GUI 包装器。
 *
 * **做什么**：把 jadx 核心反编译器的生命周期与 GUI 需求衔接起来：
 * - [open] / [close] 负责创建与销毁反编译器实例；
 * - 根据设置注册代码缓存（内存 / 磁盘）与 usage 缓存；
 * - 初始化 GUI 插件上下文，并向插件暴露项目数据。
 *
 * **线程模型**：使用 `DECOMPILER_UPDATE_SYNC` 监视器锁保护
 * [decompiler] 字段的读写，与原 Java 的 `synchronized` 块一致；[decompiler] 标记为
 * `@Volatile`。
 *
 * **为什么保留显式 getter**：这是 GUI 内部与 Java `MainWindow` 共用的 API，
 * 保持 `getXxx()` 方法名可让 Java 调用方零改动。
 */
class JadxWrapper(private val mainWindow: MainWindow) {

	@Volatile
	private var decompiler: JadxDecompiler? = null
	private var guiPluginsContext: CommonGuiPluginsContext? = null

	/** 关闭旧反编译器（若存在）并创建、加载新的反编译器实例。 */
	fun open() {
		close()
		try {
			synchronized(DECOMPILER_UPDATE_SYNC) {
				val project = getProject()
				val jadxArgs = settings.toJadxArgs()
				jadxArgs.pluginLoader = JadxExternalPluginsLoader()
				jadxArgs.filesGetter = JadxFilesGetter.INSTANCE
				project.fillJadxArgs(jadxArgs)
				JadxAppCommon.applyEnvVars(jadxArgs)

				val decompiler = JadxDecompiler(jadxArgs)
				this.decompiler = decompiler
				guiPluginsContext = initGuiPluginsContext(decompiler, mainWindow)
				initUsageCache(jadxArgs)
				registerCodeCache(decompiler)
				decompiler.setEventsImpl(mainWindow.events())
				decompiler.load()
			}
		} catch (e: Exception) {
			LOG.error("Jadx decompiler wrapper init error", e)
			close()
		}
	}

	/**
	 * 卸载所有已加载类的代码，以释放内存；
	 * 已完成处理的类标记为“已生成并卸载”，其余标记为“未加载”。
	 *
	 * TODO: 后续可考虑移入 core 包。
	 */
	fun unloadClasses() {
		currentDecompiler?.let { decompiler ->
			for (cls in checkNotNull(decompiler.getRoot()).classes) {
				val clsState: ProcessState = cls.state
				cls.unload()
				cls.state = if (clsState == PROCESS_COMPLETE) GENERATED_AND_UNLOADED else NOT_LOADED
			}
		}
	}

	/** 关闭当前反编译器与 GUI 插件上下文，并重置缓存对象。 */
	fun close() {
		try {
			synchronized(DECOMPILER_UPDATE_SYNC) {
				val decompiler = this.decompiler
				if (decompiler != null) {
					decompiler.close()
					this.decompiler = null
				}
				if (guiPluginsContext != null) {
					resetGuiPluginsContext()
					guiPluginsContext = null
				}
			}
		} catch (e: Exception) {
			LOG.error("Jadx decompiler close error", e)
		} finally {
			mainWindow.getCacheObject().reset()
		}
	}

	/**
	 * 磁盘缓存需要已加载的类才能工作，但缓存又必须在 “after load” 事件之前设置，
	 * 以便插件在启用缓存的情况下反编译。因此注册一个最后的 “prepare” pass 来初始化缓存。
	 */
	private fun registerCodeCache(jadxDecompiler: JadxDecompiler) {
		val codeCacheMode = settings.codeCacheMode
		if (codeCacheMode == CodeCacheMode.MEMORY) {
			jadxDecompiler.getArgs().codeCache = InMemoryCodeCache()
			return
		}
		jadxDecompiler.addCustomPass(object : JadxPreparePass {
			override fun getInfo(): JadxPassInfo = SimpleJadxPassInfo("CacheInit")

			override fun init(root: RootNode) {
				when (settings.codeCacheMode) {
					CodeCacheMode.DISK_WITH_CACHE ->
						root.getArgs().codeCache = CodeStringCache(buildBufferedDiskCache(root))

					CodeCacheMode.DISK ->
						root.getArgs().codeCache = buildBufferedDiskCache(root)

					else -> {}
				}
			}
		})
	}

	private fun buildBufferedDiskCache(root: RootNode): BufferCodeCache {
		val diskCache = DiskCodeCache(root, getProject().getCacheDir())
		return BufferCodeCache(diskCache)
	}

	/** 按设置初始化 usage 分析结果缓存。 */
	private fun initUsageCache(jadxArgs: JadxArgs) {
		when (settings.usageCacheMode) {
			UsageCacheMode.NONE -> jadxArgs.usageInfoCache = EmptyUsageInfoCache()

			UsageCacheMode.MEMORY -> jadxArgs.usageInfoCache = InMemoryUsageInfoCache()

			UsageCacheMode.DISK ->
				jadxArgs.usageInfoCache = UsageInfoCache(getProject().getCacheDir(), jadxArgs.inputFiles)
		}
	}

	fun getGuiPluginsContext(): CommonGuiPluginsContext = checkNotNull(guiPluginsContext)

	fun resetGuiPluginsContext() {
		checkNotNull(guiPluginsContext).reset()
	}

	fun reloadPasses() {
		resetGuiPluginsContext()
		checkNotNull(decompiler).reloadPasses()
	}

	/** 获取完整的类列表。 */
	val classes: List<JavaClass> get() = getDecompiler().getClasses()

	/** 获取未被排除包设置过滤掉的类。 */
	val includedClasses: List<JavaClass> get() {
		val classList = getDecompiler().getClasses()
		val excludedPackages = getExcludedPackages()
		if (excludedPackages.isEmpty()) {
			return classList
		}
		return classList.filter { cls -> isClassIncluded(excludedPackages, cls) }
	}

	/** 获取未被排除包设置过滤掉的类（含内部类）。 */
	val includedClassesWithInners: List<JavaClass> get() {
		val classes = getDecompiler().getClassesWithInners()
		val excludedPackages = getExcludedPackages()
		if (excludedPackages.isEmpty()) {
			return classes
		}
		return classes.filter { cls -> isClassIncluded(excludedPackages, cls) }
	}

	/** 类是否未被任何排除项命中（完全匹配或属于其子包）。 */
	private fun isClassIncluded(excludedPackages: List<String>, cls: JavaClass): Boolean {
		for (exclude in excludedPackages) {
			val clsFullName = cls.getFullName()
			if (clsFullName == exclude || clsFullName.startsWith(exclude + '.')) {
				return false
			}
		}
		return true
	}

	fun buildDecompileBatches(classes: List<JavaClass>): List<List<JavaClass>> = getDecompiler().getDecompileScheduler().buildBatches(classes)

	// TODO: 后续移到 CLI，并在 JadxDecompiler 中过滤类
	fun getExcludedPackages(): List<String> {
		val excludedPackages = settings.excludedPackages.trim()
		if (excludedPackages.isEmpty()) {
			return Collections.emptyList()
		}
		return excludedPackages.split(Regex(" +"))
	}

	fun setExcludedPackages(packagesToExclude: List<String>) {
		settings.setExcludedPackages(packagesToExclude.joinToString(" ").trim())
		settings.sync()
	}

	fun addExcludedPackage(packageToExclude: String) {
		val newExclusion = settings.excludedPackages + ' ' + packageToExclude
		settings.setExcludedPackages(newExclusion.trim())
		settings.sync()
	}

	fun removeExcludedPackage(packageToRemoveFromExclusion: String) {
		val list = ArrayList(getExcludedPackages())
		list.remove(packageToRemoveFromExclusion)
		settings.setExcludedPackages(list.joinToString(" "))
		settings.sync()
	}

	val currentDecompiler: JadxDecompiler? get() {
		synchronized(DECOMPILER_UPDATE_SYNC) {
			return decompiler
		}
	}

	/**
	 * TODO: 后续将本方法改为 private。
	 * 不要在字段中长期保存 [JadxDecompiler]，以免泄漏旧实例。
	 */
	fun getDecompiler(): JadxDecompiler {
		val decompiler = this.decompiler
		if (decompiler == null || decompiler.getRoot() == null) {
			throw JadxRuntimeException("Decompiler not yet loaded")
		}
		return decompiler
	}

	// TODO: 禁止使用本方法
	val rootNode: RootNode get() = checkNotNull(getDecompiler().getRoot())

	fun reloadCodeData() {
		getDecompiler().reloadCodeData()
	}

	fun getJavaNodeByRef(nodeRef: ICodeNodeRef?): JavaNode? {
		if (nodeRef == null) {
			return null
		}
		return getDecompiler().getJavaNodeByRef(nodeRef)
	}

	fun getEnclosingNode(codeInfo: ICodeInfo, pos: Int): JavaNode? = getDecompiler().getEnclosingNode(codeInfo, pos)

	val packages: List<JavaPackage> get() = getDecompiler().getPackages()

	val resources: List<ResourceFile> get() = getDecompiler().getResources()

	val args: JadxArgs get() = getDecompiler().getArgs()

	fun getProject(): JadxProject = mainWindow.getProject()

	val settings: JadxSettings get() = mainWindow.getSettings()

	val cache: CacheObject get() = mainWindow.getCacheObject()

	/**
	 * 按外部类别名全名搜索（仅支持外层类）。
	 *
	 * @param fullName 外层类的全名，不支持内部类。
	 */
	fun searchJavaClassByFullAlias(fullName: String): JavaClass? = getDecompiler().getClasses()
		.firstOrNull { cls -> cls.getFullName() == fullName }

	fun searchJavaClassByOrigClassName(fullName: String): JavaClass? = getDecompiler().searchJavaClassByOrigFullName(fullName)

	/**
	 * 按原始类名搜索（仅支持外层类）。
	 *
	 * @param rawName 外层类的原始名，不支持内部类。
	 */
	fun searchJavaClassByRawName(rawName: String): JavaClass? = getDecompiler().getClasses()
		.firstOrNull { cls -> cls.getRawName() == rawName }

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxWrapper::class.java)

		/** 保护 [decompiler] 字段读写的监视器。 */
		private val DECOMPILER_UPDATE_SYNC = Any()

		/**
		 * 初始化 GUI 插件上下文，并向核心插件管理器注册“新增插件”监听器，
		 * 为每个插件构建 GUI 上下文与文件访问器。
		 */
		fun initGuiPluginsContext(decompiler: JadxDecompiler, mainWindow: MainWindow): CommonGuiPluginsContext {
			val guiPluginsContext = CommonGuiPluginsContext(mainWindow)
			decompiler.getPluginManager().registerAddPluginListener(
				{ pluginContext ->
					val appContext = AppContext()
					appContext.setGuiContext(guiPluginsContext.buildForPlugin(pluginContext))
					appContext.setFilesGetter(decompiler.getArgs().filesGetter)
					pluginContext.setAppContext(appContext)
				},
			)
			return guiPluginsContext
		}
	}
}
