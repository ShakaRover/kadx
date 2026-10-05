package kadx.gui.ui.tab

import kadx.gui.ui.MainWindow
import javax.swing.Icon
import javax.swing.JPopupMenu
import javax.swing.tree.DefaultMutableTreeNode

/**
 * QuickTabs（快速标签页）树的节点基类。
 *
 * **做什么**：在 Swing 的 [DefaultMutableTreeNode] 之上补充“右键菜单”和“图标”两个界面语义。
 * 子类分为“分组父节点”（置顶 / 打开 / 书签）与“叶子节点”（具体的 [kadx.gui.treemodel.JNode]）。
 *
 * **为什么保留 [DefaultMutableTreeNode]**：`QuickTabsTree` 依赖 Swing 的 `TreeModel`
 * 事件（`nodesWereInserted` / `nodesWereRemoved`），必须保持标准树节点类型。
 *
 * **为什么不是 `data class`**：树节点存在父子互相引用，需要按身份比较。
 */
abstract class QuickTabsBaseNode : DefaultMutableTreeNode() {

	/** 节点右键菜单；无菜单时返回 `null`。 */
	open fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? = null

	/** 节点图标；无图标时返回 `null`。 */
	open fun getIcon(): Icon? = null
}
