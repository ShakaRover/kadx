package kadx.gui.ui.tab

import kadx.gui.ui.MainWindow
import kadx.gui.utils.Icons
import kadx.gui.utils.NLS
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * QuickTabs 的“书签标签页”分组节点。
 */
class QuickTabsBookmarkParentNode(tabsController: TabsController) : QuickTabsParentNode(tabsController) {

	override fun getTitle(): String = NLS.str("tree.bookmarked_tabs")

	override fun getIcon(): Icon = Icons.BOOKMARK_DARK

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? {
		if (childCount == 0) {
			return null
		}
		val menu = JPopupMenu()
		val unbookmarkAll = JMenuItem(NLS.str("tabs.unbookmark_all"))
		unbookmarkAll.addActionListener { tabsController.unbookmarkAllTabs() }
		menu.add(unbookmarkAll)
		return menu
	}
}
