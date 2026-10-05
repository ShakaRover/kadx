package kadx.api.plugins.data

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.options.KadxPluginOptions
import java.io.Closeable
import java.nio.file.Path

/**
 * 插件的运行时数据接口。
 *
 * **做什么**：暴露插件实例、元信息、代码输入、选项与输入哈希，并提供便捷的代码加载方法。
 *
 * **为什么保持 Java 可实现**：实现类 `PluginContext` 是 kadx-core 的 Java 类。
 * [loadCodeFiles] 的参数显式写成 `java.util.List`（而非 Kotlin `List`）：
 * Kotlin 的 `List<T>` 在参数位置会生成 `List<? extends T>`，与 Java 覆写方声明的
 * `List<Path>` 产生 name clash；显式 `java.util.List` 才能让 Java 实现类零改动。
 * 调用方（Kotlin）需要用 `as java.util.List<Path>` 桥接（与 `KadxCodeInput` 同样的处理）。
 */
interface KadxPluginRuntimeData {

	/** 插件是否已完成初始化。 */
	fun isInitialized(): Boolean

	/** 插件 id。 */
	fun getPluginId(): String

	/** 插件实例。 */
	fun getPluginInstance(): KadxPlugin

	/** 插件元信息。 */
	fun getPluginInfo(): KadxPluginInfo

	/** 插件注册的代码输入列表。 */
	fun getCodeInputs(): List<KadxCodeInput>

	/** 插件选项；未注册时为 null。 */
	fun getOptions(): KadxPluginOptions?

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
