package jadx.gui.settings.ui

import jadx.api.plugins.gui.ISettingsGroup
import javax.swing.tree.DefaultMutableTreeNode

/**
 * 设置树节点：包装一个 [ISettingsGroup]，节点显示文本即组的标题。
 */
class SettingsTreeNode(private val group: ISettingsGroup) : DefaultMutableTreeNode() {

	fun getGroup(): ISettingsGroup = group

	override fun toString(): String = group.getTitle()
}
