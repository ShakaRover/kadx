package jadx.api.plugins.data

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.options.JadxPluginOptions
import java.io.Closeable
import java.nio.file.Path

/**
 * 插件的运行时数据接口。
 *
 * **做什么**：暴露插件实例、元信息、代码输入、选项与输入哈希，并提供便捷的代码加载方法。
 *
 * **为什么保持 Java 可实现**：实现类 `PluginContext` 是 jadx-core 的 Java 类。
 * [loadCodeFiles] 的参数显式写成 `java.util.List`（而非 Kotlin `List`）：
 * Kotlin 的 `List<T>` 在参数位置会生成 `List<? extends T>`，与 Java 覆写方声明的
 * `List<Path>` 产生 name clash；显式 `java.util.List` 才能让 Java 实现类零改动。
 * 调用方（Kotlin）需要用 `as java.util.List<Path>` 桥接（与 `JadxCodeInput` 同样的处理）。
 */
interface JadxPluginRuntimeData {

	/** 插件是否已完成初始化。 */
	fun isInitialized(): Boolean

	/** 插件 id。 */
	fun getPluginId(): String

	/** 插件实例。 */
	fun getPluginInstance(): JadxPlugin

	/** 插件元信息。 */
	fun getPluginInfo(): JadxPluginInfo

	/** 插件注册的代码输入列表。 */
	fun getCodeInputs(): List<JadxCodeInput>

	/** 插件选项；未注册时为 null。 */
	fun getOptions(): JadxPluginOptions?

	/** 影响输出代码的输入哈希（用于判断缓存是否失效）。 */
	fun getInputsHash(): String

	/**
	 * 便捷方法：从自定义文件加载代码。
	 *
	 * @param files     待加载的文件路径列表（显式 `java.util.List` 以兼容 Java 实现类）
	 * @param closeable 可选的随代码加载器一起关闭的资源
	 */
	fun loadCodeFiles(files: java.util.List<Path>, closeable: Closeable?): ICodeLoader
}
