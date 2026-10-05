package kadx.gui.ui.startpage

import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.Icons
import kadx.gui.utils.NLS
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
