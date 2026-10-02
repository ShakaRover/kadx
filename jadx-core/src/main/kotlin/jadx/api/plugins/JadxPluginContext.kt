package jadx.api.plugins

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.plugins.data.IJadxFiles
import jadx.api.plugins.data.IJadxPlugins
import jadx.api.plugins.events.IJadxEvents
import jadx.api.plugins.gui.JadxGuiContext
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.pass.JadxPass
import jadx.api.plugins.resources.IResourcesLoader
import jadx.zip.ZipReader
import org.jetbrains.annotations.Nullable
import java.util.function.Supplier

/**
 * 插件运行时上下文：插件通过它访问 jadx 能力并注册自己的扩展。
 *
 * **做什么**：提供参数、反编译器、资源加载器、GUI 上下文、事件总线等访问入口，
 * 以及注册 pass / 代码输入 / 选项的方法。
 *
 * **为什么保留显式 getter**：这是插件公共 API，Java 插件与 jadx-cli/gui 都直接调用
 * `getXxx()`，因此不使用 Kotlin 属性，保持 JVM 方法名与原 Java 一致。
 */
interface JadxPluginContext {

	fun getArgs(): JadxArgs

	fun getDecompiler(): JadxDecompiler

	fun addPass(pass: JadxPass)

	fun addCodeInput(codeInput: JadxCodeInput)

	fun registerOptions(options: JadxPluginOptions)

	/**
	 * 注册一个用于计算「会影响输出代码的所有选项」哈希的函数。
	 * 默认实现已经会计算输入文件（[JadxArgs.getInputFiles]）与已注册选项的哈希，
	 * 插件若有额外会影响输出的配置，可通过本方法补充。
	 */
	fun registerInputsHashSupplier(supplier: Supplier<String>)

	/**
	 * 自定义资源加载。
	 */
	fun getResourcesLoader(): IResourcesLoader

	/**
	 * 访问 jadx-gui 专属能力（非 GUI 环境下返回 null）。
	 */
	@Nullable
	fun getGuiContext(): JadxGuiContext?

	/**
	 * 订阅与发送事件。
	 */
	fun events(): IJadxEvents

	/**
	 * 访问已注册的插件及其运行时数据。
	 */
	fun plugins(): IJadxPlugins

	/**
	 * 访问插件专属的文件与目录。
	 */
	fun files(): IJadxFiles

	/**
	 * 自定义 jadx zip 读取器，用于对抗篡改并提供额外的安全检查。
	 */
	fun getZipReader(): ZipReader
}
