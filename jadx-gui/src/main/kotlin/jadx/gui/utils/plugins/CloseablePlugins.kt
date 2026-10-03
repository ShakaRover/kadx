package jadx.gui.utils.plugins

import jadx.core.plugins.PluginContext

/**
 * 「可关闭的插件集合」。
 *
 * **做什么**：把一批已解析的 [PluginContext] 与一个可选的清理回调打包在一起。
 * 设置窗口关闭时调用 [close]，从而卸载在临时上下文中加载的插件。
 *
 * **为什么保留显式 getter**：`PluginSettings` 等 Kotlin 调用方按 `getList()` /
 * `getCloseable()` 命名访问；同时 Java 调用方也能零改动使用。
 */
class CloseablePlugins(private val list: List<PluginContext>, private val closeable: Runnable?) {

	/** 执行清理回调（若存在）。 */
	fun close() {
		closeable?.run()
	}

	/** 清理回调；未提供时为 `null`。 */
	fun getCloseable(): Runnable? = closeable

	/** 插件上下文列表。 */
	fun getList(): List<PluginContext> = list
}
