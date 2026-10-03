package jadx.gui.ui.startpage

import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import javax.swing.Icon

/**
 * 起始页的树节点。
 *
 * **做什么**：作为标签页的根节点，关联 [StartPagePanel] 内容面板；不参与快速标签页。
 */
class StartPageNode : JNode() {

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = StartPagePanel(tabbedPane, this)

	override fun makeString(): String = NLS.str("start_page.title")

	override fun getIcon(): Icon = Icons.START_PAGE

	override fun getJParent(): JClass? = null

	override fun supportsQuickTabs(): Boolean = false

	companion object {
		private const val serialVersionUID: Long = 8983134608645736174L
	}
}
