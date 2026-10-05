package kadx.core.plugins

import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.data.IKadxFiles
import kadx.api.plugins.data.IKadxPlugins
import kadx.api.plugins.data.KadxPluginRuntimeData
import kadx.api.plugins.events.IKadxEvents
import kadx.api.plugins.gui.KadxGuiContext
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.input.data.impl.MergeCodeLoader
import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.options.OptionDescription
import kadx.api.plugins.options.OptionFlag
import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.resources.IResourcesLoader
import kadx.core.plugins.files.KadxFilesData
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils
import kadx.zip.ZipReader
import java.io.Closeable
import java.nio.file.Path
import java.util.function.Supplier

/**
 * 插件项目级上下文：同时实现 [KadxPluginContext]（对插件暴露能力）与
 * [KadxPluginRuntimeData]（对宿主暴露插件数据）。
 *
 * **做什么**：绑定到某个 `KadxDecompiler`，收集插件注册的代码输入，
 * 并把插件元信息、选项、生命周期状态委托给 [PluginRuntime]。
 *
 * **为什么保留显式 getter**：这是插件公共 API 与 kadx-gui/cli 直接调用的类型，
 * 所有 `getXxx()` 保持 JVM 方法名不变，Java 调用方零改动。
 *
 * **equals/hashCode/compareTo 语义**：按插件 id 比较（不是对象身份），
 * 因为同一插件在不同上下文中可能产生多个实例，但 id 必须唯一。
 */
class PluginContext internal constructor(
	private val decompiler: KadxDecompiler,
	private val pluginsData: KadxPluginsData,
	private val pluginRuntime: PluginRuntime,
) : KadxPluginContext,
	KadxPluginRuntimeData,
	Comparable<PluginContext> {

	private val codeInputs: MutableList<KadxCodeInput> = ArrayList()
	private var inputsHashSupplier: Supplier<String>? = null

	override fun isInitialized(): Boolean = pluginRuntime.isInitialized

	override fun getArgs(): KadxArgs = decompiler.getArgs()

	override fun getDecompiler(): KadxDecompiler = decompiler

	override fun addPass(pass: KadxPass) {
		decompiler.addCustomPass(pass)
	}

	override fun addCodeInput(codeInput: KadxCodeInput) {
		this.codeInputs.add(codeInput)
	}

	override fun getCodeInputs(): List<KadxCodeInput> = codeInputs

	override fun registerOptions(options: KadxPluginOptions) {
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
			throw KadxRuntimeException("Failed to get inputs hash for plugin: " + getPluginId(), e)
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

	override fun events(): IKadxEvents = decompiler.events()

	override fun getResourcesLoader(): IResourcesLoader = decompiler.getResourcesLoader()

	override fun getGuiContext(): KadxGuiContext? = pluginRuntime.appContext?.getGuiContext()

	override fun getPluginInstance(): KadxPlugin = pluginRuntime.pluginInstance

	override fun getPluginInfo(): KadxPluginInfo = pluginRuntime.pluginInfo

	override fun getPluginId(): String = pluginRuntime.pluginId

	override fun getOptions(): KadxPluginOptions? = pluginRuntime.options

	override fun plugins(): IKadxPlugins = pluginsData

	override fun files(): IKadxFiles = KadxFilesData(getPluginInfo(), checkNotNull(pluginRuntime.appContext).getFilesGetter())

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
