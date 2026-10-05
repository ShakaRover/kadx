package kadx.api.plugins

import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.api.plugins.data.IKadxFiles
import kadx.api.plugins.data.IKadxPlugins
import kadx.api.plugins.events.IKadxEvents
import kadx.api.plugins.gui.KadxGuiContext
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.resources.IResourcesLoader
import kadx.zip.ZipReader
import org.jetbrains.annotations.Nullable
import java.util.function.Supplier

/**
 * 插件运行时上下文：插件通过它访问 kadx 能力并注册自己的扩展。
 *
 * **做什么**：提供参数、反编译器、资源加载器、GUI 上下文、事件总线等访问入口，
 * 以及注册 pass / 代码输入 / 选项的方法。
 *
 * **为什么保留显式 getter**：这是插件公共 API，Java 插件与 kadx-cli/gui 都直接调用
 * `getXxx()`，因此不使用 Kotlin 属性，保持 JVM 方法名与原 Java 一致。
 */
interface KadxPluginContext {

	fun getArgs(): KadxArgs

	fun getDecompiler(): KadxDecompiler

	fun addPass(pass: KadxPass)

	fun addCodeInput(codeInput: KadxCodeInput)

	fun registerOptions(options: KadxPluginOptions)

	/**
	 * 注册一个用于计算「会影响输出代码的所有选项」哈希的函数。
	 * 默认实现已经会计算输入文件（[KadxArgs.getInputFiles]）与已注册选项的哈希，
	 * 插件若有额外会影响输出的配置，可通过本方法补充。
	 */
	fun registerInputsHashSupplier(supplier: Supplier<String>)

	/**
	 * 自定义资源加载。
	 */
	fun getResourcesLoader(): IResourcesLoader

	/**
	 * 访问 kadx-gui 专属能力（非 GUI 环境下返回 null）。
	 */
	@Nullable
	fun getGuiContext(): KadxGuiContext?

	/**
	 * 订阅与发送事件。
	 */
	fun events(): IKadxEvents

	/**
	 * 访问已注册的插件及其运行时数据。
	 */
	fun plugins(): IKadxPlugins

	/**
	 * 访问插件专属的文件与目录。
	 */
	fun files(): IKadxFiles

	/**
	 * 自定义 kadx zip 读取器，用于对抗篡改并提供额外的安全检查。
	 */
	fun getZipReader(): ZipReader
}
