package jadx.gui.ui.tab

import jadx.gui.ui.MainWindow
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * QuickTabs 的“置顶标签页”分组节点。
 */
class QuickTabsPinParentNode(tabsController: TabsController) : QuickTabsParentNode(tabsController) {

	override fun getTitle(): String = NLS.str("tree.pinned_tabs")

	override fun getIcon(): Icon = Icons.PIN

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? {
		if (childCount == 0) {
			return null
		}
		val menu = JPopupMenu()
		val unpinAll = JMenuItem(NLS.str("tabs.unpin_all"))
		unpinAll.addActionListener { tabsController.unpinAllTabs() }
		menu.add(unpinAll)
		return menu
	}
}
