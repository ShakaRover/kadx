package jadx.gui.ui.treenodes

import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.panel.UndisplayedStringsPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import javax.swing.Icon

/**
 * “未显示字符串”节点：展示反编译过程中被忽略的不可见/不可显示字符串。
 */
class UndisplayedStringsNode(private val undisplayedStings: String) : JNode() {

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = UndisplayedStringsPanel(tabbedPane, this)

	override fun makeString(): String = NLS.str("msg.non_displayable_chars.title")

	override fun getIcon(): Icon = Icons.FONT

	override fun getJParent(): JClass? = null

	override fun makeDescString(): String? = undisplayedStings

	override fun supportsQuickTabs(): Boolean = false

	companion object {
		private const val serialVersionUID: Long = 2005158949697898302L
	}
}
