package jadx.gui.utils.plugins

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.options.JadxPluginOptions

/**
 * 插件与其选项的二元组，供设置界面排序与展示。
 *
 * **做什么**：持有 [JadxPlugin] 和它对应的 [JadxPluginOptions]，并实现 [Comparable]
 * 以便按类名排序；[NULL] 是空占位单例。
 *
 * **为什么保持 Java 可实现**：这是插件相关工具类，字段与构造器保持原有形态，
 * 外部（可能仍是 Java 的）插件代码可继续 `new PluginWithOptions(...)`。
 *
 * **注意**：[compareTo] 保留原 Java 的实现（比较的是 `other` 自身的类名，
 * 而非 `other.plugin` 的类名），以维持完全一致的行为。
 */
class PluginWithOptions(private val plugin: JadxPlugin?, private val options: JadxPluginOptions?) : Comparable<PluginWithOptions> {

	fun getPlugin(): JadxPlugin? = plugin

	fun getOptions(): JadxPluginOptions? = options

	override fun compareTo(other: PluginWithOptions): Int {
		// 原 Java 即为此实现（比较 other 自身的类名），此处保持不变
		return checkNotNull(plugin).javaClass.name.compareTo(other.javaClass.name)
	}

	override fun toString(): String = "PluginWithOptions{plugin=" + plugin + ", options=" + options + '}'

	companion object {
		/** 空占位单例。 */
		@JvmField
		val NULL = PluginWithOptions(null, null)
	}
}
