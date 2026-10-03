package jadx.gui.settings.ui.plugins

import jadx.plugins.tools.data.JadxPluginListEntry

/**
 * 「可用插件」节点：来自远程插件列表的条目，操作为 [PluginAction.INSTALL]。
 */
class AvailablePluginNode(private val metadata: JadxPluginListEntry) : BasePluginListNode() {

	override fun getTitle(): String? = metadata.name

	override fun hasDetails(): Boolean = true

	override fun getPluginId(): String? = metadata.pluginId

	override fun getDescription(): String? = metadata.description

	override fun getHomepage(): String? = metadata.homepage

	override fun getLocationId(): String? = metadata.locationId

	override fun getAction(): PluginAction = PluginAction.INSTALL
}
