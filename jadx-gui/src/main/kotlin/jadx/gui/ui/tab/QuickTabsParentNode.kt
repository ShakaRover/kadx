package jadx.gui.ui.tab

import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import javax.swing.JPopupMenu

/**
 * QuickTabs 分组父节点：维护 [JNode] 到 [QuickTabsChildNode] 的映射，
 * 保证同一个节点在树中只出现一次。
 *
 * **做什么**：提供按 [JNode] 增删子节点的操作，并在增删时由 `QuickTabsTree`
 * 触发对应的 `TreeModel` 事件。具体分组（置顶 / 打开 / 书签）由子类实现 [getTitle]。
 *
 * **为什么用 [childrenMap]**：Swing 树按索引查找子节点是 O(n)，
 * 这里额外用哈希表把按节点的查找降为 O(1)，并借此判断节点是否已存在。
 */
abstract class QuickTabsParentNode(protected val tabsController: TabsController) : QuickTabsBaseNode() {

	/** 节点 -> 子节点 的索引，保证不重复插入。 */
	private val childrenMap: MutableMap<JNode, QuickTabsChildNode> = HashMap()

	/**
	 * 添加节点；已存在时返回 `false`。
	 */
	fun addJNode(node: JNode): Boolean {
		if (childrenMap.containsKey(node)) {
			return false
		}
		val childNode = QuickTabsChildNode(node)
		childrenMap[node] = childNode
		add(childNode)
		return true
	}

	/**
	 * 移除节点；不存在时返回 `false`。
	 */
	fun removeJNode(node: JNode): Boolean {
		val childNode = childrenMap.remove(node) ?: return false
		remove(childNode)
		return true
	}

	/** 移除全部子节点（不清空索引，调用方需保证索引同步；实际由 [removeJNode] 维护）。 */
	fun removeAllNodes() {
		removeAllChildren()
	}

	/** 查找节点对应的子节点；不存在时返回 `null`。 */
	fun getQuickTabsNode(node: JNode): QuickTabsChildNode? = childrenMap[node]

	/** 分组标题（显示在树上）。 */
	abstract fun getTitle(): String

	override fun toString(): String = getTitle()

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? = super.onTreePopupMenu(mainWindow)
}
