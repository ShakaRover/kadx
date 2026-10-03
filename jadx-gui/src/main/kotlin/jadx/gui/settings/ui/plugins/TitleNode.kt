package jadx.gui.settings.ui.plugins

/**
 * 列表分组标题节点（如 "Installed" / "Available" / "Bundled"），没有详情。
 */
class TitleNode(private val title: String) : BasePluginListNode() {

	override fun getTitle(): String = title

	override fun hasDetails(): Boolean = false
}
