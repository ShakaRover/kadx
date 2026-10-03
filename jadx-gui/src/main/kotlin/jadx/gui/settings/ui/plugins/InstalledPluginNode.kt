package jadx.gui.settings.ui.plugins

import jadx.plugins.tools.data.JadxPluginMetadata

/**
 * 「已安装插件」节点：操作为 [PluginAction.UNINSTALL]，并携带启用/禁用状态。
 */
class InstalledPluginNode(private val metadata: JadxPluginMetadata) : BasePluginListNode() {

	override fun getTitle(): String? = metadata.name

	override fun hasDetails(): Boolean = true

	override fun getPluginId(): String? = metadata.pluginId

	override fun getDescription(): String? = metadata.description

	override fun getHomepage(): String? = metadata.homepage

	override fun getAction(): PluginAction = PluginAction.UNINSTALL

	override fun getVersion(): String? = metadata.version

	override fun isDisabled(): Boolean = metadata.isDisabled()

	override fun toString(): String = metadata.name ?: ""
}
