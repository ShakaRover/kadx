package jadx.api.plugins.options

/**
 * 插件选项接口：插件通过它声明可配置项并接收用户设置。
 *
 * **为什么方法名保持 Java 风格**：Java 插件直接实现本接口，
 * `setOptions` / `getOptionsDescriptions` 的 JVM 名必须与原 Java 一致。
 */
interface JadxPluginOptions {

	fun setOptions(options: Map<String, String>)

	fun getOptionsDescriptions(): List<OptionDescription>
}
