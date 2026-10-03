package jadx.gui.ui.tab

import jadx.gui.ui.MainWindow
import jadx.gui.utils.Icons
import jadx.gui.utils.NLS
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * QuickTabs 的“已打开标签页”分组节点。
 */
class QuickTabsOpenParentNode(tabsController: TabsController) : QuickTabsParentNode(tabsController) {

	override fun getTitle(): String = NLS.str("tree.open_tabs")

	override fun getIcon(): Icon = Icons.FOLDER

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? {
		if (childCount == 0) {
			return null
		}
		val menu = JPopupMenu()
		val closeAll = JMenuItem(NLS.str("tabs.closeAll"))
		closeAll.addActionListener { tabsController.closeAllTabs(true) }
		menu.add(closeAll)
		return menu
	}
}
