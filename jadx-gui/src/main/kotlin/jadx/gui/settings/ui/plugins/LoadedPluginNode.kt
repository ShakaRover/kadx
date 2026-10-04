package jadx.gui.settings.ui.plugins

import jadx.core.plugins.PluginRuntime

/**
 * 「已加载插件」节点：来自运行时已加载但未安装到磁盘的插件。
 */
class LoadedPluginNode(private val plugin: PluginRuntime) : BasePluginListNode() {

	override fun getTitle(): String? = plugin.pluginInfo.getName()

	override fun hasDetails(): Boolean = true

	override fun getPluginId(): String? = plugin.pluginId

	override fun getDescription(): String? = plugin.pluginInfo.getDescription()

	override fun getHomepage(): String? = plugin.pluginInfo.getHomepage()

	override fun toString(): String = plugin.pluginInfo.getName()
}
