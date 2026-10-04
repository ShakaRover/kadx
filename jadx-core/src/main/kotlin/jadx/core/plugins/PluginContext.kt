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
 * 插件项目级上下文：同时实现 [JadxPluginContext]（对插件暴露能力）与
 * [JadxPluginRuntimeData]（对宿主暴露插件数据）。
 *
 * **做什么**：绑定到某个 `JadxDecompiler`，收集插件注册的代码输入，
 * 并把插件元信息、选项、生命周期状态委托给 [PluginRuntime]。
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
	private val pluginRuntime: PluginRuntime,
) : JadxPluginContext,
	JadxPluginRuntimeData,
	Comparable<PluginContext> {

	private val codeInputs: MutableList<JadxCodeInput> = ArrayList()
	private var inputsHashSupplier: Supplier<String>? = null

	override fun isInitialized(): Boolean = pluginRuntime.isInitialized

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
		pluginRuntime.registerOptions(options)
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
		val options = pluginRuntime.options ?: return ""
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

	override fun getGuiContext(): JadxGuiContext? = pluginRuntime.appContext?.getGuiContext()

	override fun getPluginInstance(): JadxPlugin = pluginRuntime.pluginInstance

	override fun getPluginInfo(): JadxPluginInfo = pluginRuntime.pluginInfo

	override fun getPluginId(): String = pluginRuntime.pluginId

	override fun getOptions(): JadxPluginOptions? = pluginRuntime.options

	override fun plugins(): IJadxPlugins = pluginsData

	override fun files(): IJadxFiles = JadxFilesData(getPluginInfo(), checkNotNull(pluginRuntime.appContext).getFilesGetter())

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
