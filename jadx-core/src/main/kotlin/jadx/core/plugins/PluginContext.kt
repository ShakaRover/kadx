package jadx.core.plugins

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.data.IJadxFiles
import jadx.api.plugins.data.IJadxPlugins
import jadx.api.plugins.data.JadxPluginRuntimeData
import jadx.api.plugins.events.IJadxEvents
import jadx.api.plugins.gui.JadxGuiContext
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.input.data.impl.MergeCodeLoader
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.OptionDescription
import jadx.api.plugins.options.OptionFlag
import jadx.api.plugins.pass.JadxPass
import jadx.api.plugins.resources.IResourcesLoader
import jadx.core.plugins.files.JadxFilesData
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.zip.ZipReader
import java.io.Closeable
import java.nio.file.Path
import java.util.function.Supplier

/**
 * 插件运行时上下文：同时实现 [JadxPluginContext]（对插件暴露能力）与
 * [JadxPluginRuntimeData]（对宿主暴露插件数据）。
 *
 * **做什么**：持有插件实例、元信息、类加载器、代码输入、选项与输入哈希；
 * 负责在插件的类加载器上下文中调用 `init` / `unload`。
 *
 * **为什么保留显式 getter**：这是插件公共 API 与 jadx-gui/cli 直接调用的类型，
 * 所有 `getXxx()` 保持 JVM 方法名不变，Java 调用方零改动。
 *
 * **equals/hashCode/compareTo 语义**：按插件 id 比较（不是对象身份），
 * 因为同一插件在不同上下文中可能产生多个实例，但 id 必须唯一。
 */
class PluginContext internal constructor(
	private val decompiler: JadxDecompiler,
	private val pluginsData: JadxPluginsData,
	private val plugin: JadxPlugin,
) : JadxPluginContext,
	JadxPluginRuntimeData,
	Comparable<PluginContext> {

	private val pluginInfo: JadxPluginInfo = plugin.getPluginInfo()
	private val pluginClassLoader: ClassLoader = plugin.javaClass.classLoader

	/** 应用级上下文；由宿主在 init 前注入。 */
	private var appContext: AppContext? = null

	private val codeInputs: MutableList<JadxCodeInput> = ArrayList()
	private var options: JadxPluginOptions? = null
	private var inputsHashSupplier: Supplier<String>? = null

	private var initialized: Boolean = false

	/** 初始化插件：在插件类加载器上下文中调用 [JadxPlugin.init]。 */
	fun init() {
		classLoaderWrap {
			plugin.init(this)
			initialized = true
		}
	}

	/** 卸载插件：仅在已初始化时在插件类加载器上下文中调用 [JadxPlugin.unload]。 */
	fun unload() {
		if (initialized) {
			classLoaderWrap { plugin.unload() }
		}
	}

	/** 临时把当前线程的上下文类加载器切换为插件类加载器，执行任务后恢复。 */
	fun classLoaderWrap(task: Runnable) {
		val thread = Thread.currentThread()
		val prevClassLoader = thread.contextClassLoader
		thread.contextClassLoader = pluginClassLoader
		try {
			task.run()
		} finally {
			thread.contextClassLoader = prevClassLoader
		}
	}

	override fun isInitialized(): Boolean = initialized

	override fun getArgs(): JadxArgs = decompiler.getArgs()

	override fun getDecompiler(): JadxDecompiler = decompiler

	override fun addPass(pass: JadxPass) {
		decompiler.addCustomPass(pass)
	}

	override fun addCodeInput(codeInput: JadxCodeInput) {
		this.codeInputs.add(codeInput)
	}

	override fun getCodeInputs(): List<JadxCodeInput> = codeInputs

	override fun registerOptions(options: JadxPluginOptions) {
		try {
			this.options = requireNotNull(options)
			options.setOptions(getArgs().pluginOptions)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to apply options for plugin: " + getPluginId(), e)
		}
	}

	override fun registerInputsHashSupplier(supplier: Supplier<String>) {
		this.inputsHashSupplier = supplier
	}

	override fun getInputsHash(): String {
		val supplier = inputsHashSupplier
		if (supplier == null) {
			return defaultOptionsHash()
		}
		try {
			return supplier.get()
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to get inputs hash for plugin: " + getPluginId(), e)
		}
	}

	/** 默认输入哈希：拼接所有「会改变输出代码」的选项值后取 MD5。 */
	private fun defaultOptionsHash(): String {
		val options = this.options ?: return ""
		val allOptions = getArgs().pluginOptions
		val sb = StringBuilder()
		for (optDesc in options.getOptionsDescriptions()) {
			if (!optDesc.getFlags().contains(OptionFlag.NOT_CHANGING_CODE)) {
				sb.append(':').append(allOptions[optDesc.name()])
			}
		}
		return FileUtils.md5Sum(sb.toString())
	}

	override fun events(): IJadxEvents = decompiler.events()

	override fun getResourcesLoader(): IResourcesLoader = decompiler.getResourcesLoader()

	fun getAppContext(): AppContext? = appContext

	fun setAppContext(appContext: AppContext) {
		this.appContext = appContext
	}

	override fun getGuiContext(): JadxGuiContext? = requireNotNull(appContext).getGuiContext()

	override fun getPluginInstance(): JadxPlugin = plugin

	override fun getPluginInfo(): JadxPluginInfo = pluginInfo

	override fun getPluginId(): String = pluginInfo.getPluginId()

	override fun getOptions(): JadxPluginOptions? = options

	override fun plugins(): IJadxPlugins = pluginsData

	override fun files(): IJadxFiles = JadxFilesData(pluginInfo, requireNotNull(appContext).getFilesGetter())

	@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
	override fun loadCodeFiles(files: java.util.List<Path>, closeable: Closeable?): ICodeLoader = MergeCodeLoader(
		Utils.collectionMap(codeInputs) { codeInput -> codeInput.loadFiles(files) },
		closeable,
	)

	override fun getZipReader(): ZipReader = decompiler.getZipReader()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is PluginContext) {
			return false
		}
		return this.getPluginId() == other.getPluginId()
	}

	override fun hashCode(): Int = getPluginId().hashCode()

	override fun compareTo(other: PluginContext): Int = this.getPluginId().compareTo(other.getPluginId())

	override fun toString(): String = getPluginId()
}
