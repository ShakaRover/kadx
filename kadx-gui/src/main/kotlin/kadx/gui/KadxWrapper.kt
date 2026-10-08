package kadx.gui

import kadx.api.ICodeInfo
import kadx.api.JavaClass
import kadx.api.JavaNode
import kadx.api.JavaPackage
import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.api.ResourceFile
import kadx.api.impl.BoundedMemoryCodeCache
import kadx.api.metadata.ICodeNodeRef
import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.impl.SimpleKadxPassInfo
import kadx.api.plugins.pass.types.KadxPreparePass
import kadx.api.usage.impl.EmptyUsageInfoCache
import kadx.api.usage.impl.InMemoryUsageInfoCache
import kadx.cli.KadxAppCommon
import kadx.cli.plugins.KadxFilesGetter
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.ProcessState
import kadx.core.dex.nodes.ProcessState.GENERATED_AND_UNLOADED
import kadx.core.dex.nodes.ProcessState.NOT_LOADED
import kadx.core.dex.nodes.ProcessState.PROCESS_COMPLETE
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.cache.code.CodeCacheMode
import kadx.gui.cache.code.CodeStringCache
import kadx.gui.cache.code.disk.BufferCodeCache
import kadx.gui.cache.code.disk.DiskCodeCache
import kadx.gui.cache.usage.UsageCacheMode
import kadx.gui.cache.usage.UsageInfoCache
import kadx.gui.settings.KadxProject
import kadx.gui.settings.KadxSettings
import kadx.gui.ui.MainWindow
import kadx.gui.utils.CacheObject
import kadx.gui.utils.StartupTimer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections

/**
 * 核心 [KadxDecompiler] 的 GUI 包装器。
 *
 * **做什么**：把 kadx 核心反编译器的生命周期与 GUI 需求衔接起来：
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
class KadxWrapper(private val mainWindow: MainWindow) {

	@Volatile
	private var decompiler: KadxDecompiler? = null

	/** 关闭旧反编译器（若存在）并创建、加载新的反编译器实例。 */
	fun open() {
		close()
		try {
			synchronized(DECOMPILER_UPDATE_SYNC) {
				val project = getProject()
				val guiPluginsManager = mainWindow.getGuiPluginsManager()
				val kadxArgs = settings.toKadxArgs()
				kadxArgs.pluginLoader = guiPluginsManager.buildProjectPluginLoader()
				kadxArgs.filesGetter = KadxFilesGetter.INSTANCE
				project.fillKadxArgs(kadxArgs)
				KadxAppCommon.applyEnvVars(kadxArgs)

				val decompiler = KadxDecompiler(kadxArgs)
				this.decompiler = decompiler
				guiPluginsManager.initGuiPluginsContext(decompiler.getPluginManager(), kadxArgs, false)
				guiPluginsManager.injectGlobalPlugins(decompiler)
				initUsageCache(kadxArgs)
				registerCodeCache(decompiler)
				decompiler.setEventsImpl(mainWindow.events())
				StartupTimer.mark("load.begin")
				decompiler.load()
				StartupTimer.mark("load.end")
			}
		} catch (e: Exception) {
			LOG.error("Kadx decompiler wrapper init error", e)
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
				mainWindow.getGuiPluginsManager().resetProjectScope()
			}
		} catch (e: Exception) {
			LOG.error("Kadx decompiler close error", e)
		} finally {
			mainWindow.getCacheObject().reset()
		}
	}

	/**
	 * 磁盘缓存需要已加载的类才能工作，但缓存又必须在 “after load” 事件之前设置，
	 * 以便插件在启用缓存的情况下反编译。因此注册一个最后的 “prepare” pass 来初始化缓存。
	 */
	private fun registerCodeCache(kadxDecompiler: KadxDecompiler) {
		// 按可空处理：Gson 遇到未知枚举值时会把非空字段置 null（反射绕过 Kotlin 空检查）。
		// 若不显式处理，下面 when 会落到兜底分支，args.codeCache 会静默保留 KadxArgs 默认的
		// **无界** InMemoryCodeCache（全量搜索下线性增长）。
		val codeCacheMode: CodeCacheMode? = settings.codeCacheMode
		if (codeCacheMode == null) {
			LOG.warn("Unknown code cache mode in settings, falling back to DISK")
		}
		if (codeCacheMode == CodeCacheMode.MEMORY) {
			// 内存模式也必须有界：无界缓存在全量搜索时会线性增长（实测外推 253k 类约 8.5 GB）
			kadxDecompiler.getArgs().codeCache = BoundedMemoryCodeCache()
			return
		}
		val mode: CodeCacheMode = codeCacheMode ?: CodeCacheMode.DISK
		kadxDecompiler.addCustomPass(object : KadxPreparePass {
			override fun getInfo(): KadxPassInfo = SimpleKadxPassInfo("CacheInit")

			override fun init(root: RootNode) {
				// 穷尽分支（无 else）：新增枚举值时会编译报错，而不是静默用错缓存
				when (mode) {
					CodeCacheMode.DISK_WITH_CACHE ->
						root.getArgs().codeCache = CodeStringCache(buildBufferedDiskCache(root))

					CodeCacheMode.DISK ->
						root.getArgs().codeCache = buildBufferedDiskCache(root)

					CodeCacheMode.MEMORY ->
						// 已在 registerCodeCache 提前处理；保留分支使 when 穷尽
						root.getArgs().codeCache = BoundedMemoryCodeCache()
				}
			}
		})
	}

	private fun buildBufferedDiskCache(root: RootNode): BufferCodeCache {
		val diskCache = DiskCodeCache(root, getProject().getCacheDir())
		return BufferCodeCache(diskCache)
	}

	/** 按设置初始化 usage 分析结果缓存。 */
	private fun initUsageCache(kadxArgs: KadxArgs) {
		when (settings.usageCacheMode) {
			UsageCacheMode.NONE -> kadxArgs.usageInfoCache = EmptyUsageInfoCache()

			UsageCacheMode.MEMORY -> kadxArgs.usageInfoCache = InMemoryUsageInfoCache()

			UsageCacheMode.DISK ->
				kadxArgs.usageInfoCache = UsageInfoCache(getProject().getCacheDir(), kadxArgs.inputFiles)
		}
	}

	fun reloadPasses() {
		mainWindow.getGuiPluginsManager().resetProjectScope()
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

	// TODO: 后续移到 CLI，并在 KadxDecompiler 中过滤类
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

	val currentDecompiler: KadxDecompiler? get() {
		synchronized(DECOMPILER_UPDATE_SYNC) {
			return decompiler
		}
	}

	/**
	 * TODO: 后续将本方法改为 private。
	 * 不要在字段中长期保存 [KadxDecompiler]，以免泄漏旧实例。
	 */
	fun getDecompiler(): KadxDecompiler {
		val decompiler = this.decompiler
		if (decompiler == null || decompiler.getRoot() == null) {
			throw KadxRuntimeException("Decompiler not yet loaded")
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

	val args: KadxArgs get() = getDecompiler().getArgs()

	fun getProject(): KadxProject = mainWindow.getProject()

	val settings: KadxSettings get() = mainWindow.getSettings()

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
		private val LOG: Logger = LoggerFactory.getLogger(KadxWrapper::class.java)

		/** 保护 [decompiler] 字段读写的监视器。 */
		private val DECOMPILER_UPDATE_SYNC = Any()
	}
}
