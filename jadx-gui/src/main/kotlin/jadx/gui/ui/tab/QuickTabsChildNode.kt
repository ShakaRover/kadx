package jadx.gui.ui.tab

import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * QuickTabs 叶子节点：包装一个具体的 [JNode]。
 *
 * **做什么**：显示节点自身的图标，并在右键时根据父分组（置顶 / 书签）
 * 追加“关闭 / 取消置顶 / 取消书签”等菜单项。
 *
 * **为什么不是 `data class`**：树节点需要按身份比较。
 */
class QuickTabsChildNode(private val node: JNode) : QuickTabsBaseNode() {

	override fun toString(): String = node.toString()

	/** 返回被包装的树节点。 */
	val jNode: JNode get() = node

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? {
		var menu = node.onTreePopupMenu(mainWindow)

		if (node.supportsQuickTabs()) {
			if (getParent() is QuickTabsPinParentNode) {
				if (menu == null) {
					menu = JPopupMenu()
				}
				val closeAction = JMenuItem(NLS.str("tabs.close"))
				closeAction.addActionListener { mainWindow.getTabsController().closeTab(node, true) }
				menu.add(closeAction, 0)
				menu.add(JPopupMenu.Separator(), 1)
			}
			if (getParent() is QuickTabsPinParentNode) {
				if (menu == null) {
					menu = JPopupMenu()
				}
				val unpinAction = JMenuItem(NLS.str("tabs.unpin"))
				unpinAction.addActionListener { mainWindow.getTabsController().setTabPinned(node, false) }
				menu.add(unpinAction, 0)
				menu.add(JPopupMenu.Separator(), 1)
			}
			if (getParent() is QuickTabsBookmarkParentNode) {
				if (menu == null) {
					menu = JPopupMenu()
				}
				val unbookmarkAction = JMenuItem(NLS.str("tabs.unbookmark"))
				unbookmarkAction.addActionListener { mainWindow.getTabsController().setTabBookmarked(node, false) }
				menu.add(unbookmarkAction, 0)
				menu.add(JPopupMenu.Separator(), 1)
			}
		}

		return menu
	}

	override fun getIcon(): Icon? = node.getIcon()
}
