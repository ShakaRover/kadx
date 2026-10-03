package jadx.gui.settings.ui.plugins

import jadx.core.plugins.PluginContext

/**
 * 「已加载插件」节点：来自运行时已加载但未安装到磁盘的插件。
 */
class LoadedPluginNode(private val plugin: PluginContext) : BasePluginListNode() {

	override fun getTitle(): String? = plugin.getPluginInfo().getName()

	override fun hasDetails(): Boolean = true

	override fun getPluginId(): String? = plugin.getPluginId()

	override fun getDescription(): String? = plugin.getPluginInfo().getDescription()

	override fun getHomepage(): String? = plugin.getPluginInfo().getHomepage()

	override fun toString(): String = plugin.getPluginInfo().getName()
}
