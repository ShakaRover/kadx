package jadx.gui.ui.tab

import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow
import jadx.gui.utils.UiUtils
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

/**
 * “快速标签页”树：以分组形式展示已打开 / 已置顶 / 已加书签的标签页。
 *
 * **做什么**：监听 [TabsController] 的状态变化，把对应节点插入或移出三个分组；
 * 单击叶子节点即选中对应标签页。
 *
 * **为什么保留 Swing 线程模型**：所有更新都发生在 EDT（由 `TabsController` 触发），
 * 不额外起线程。
 *
 * **为什么不是 `data class`**：这是有状态的 Swing 组件。
 */
class QuickTabsTree(private val mainWindow: MainWindow) :
	JTree(),
	ITabStatesListener,
	TreeSelectionListener {

	private val treeModel: DefaultTreeModel

	private val openParentNode: QuickTabsParentNode
	private val pinParentNode: QuickTabsParentNode
	private val bookmarkParentNode: QuickTabsParentNode

	init {
		mainWindow.getTabsController().addListener(this)

		val root = Root()
		pinParentNode = QuickTabsPinParentNode(mainWindow.getTabsController())
		openParentNode = QuickTabsOpenParentNode(mainWindow.getTabsController())
		bookmarkParentNode = QuickTabsBookmarkParentNode(mainWindow.getTabsController())
		root.add(openParentNode)
		root.add(pinParentNode)
		root.add(bookmarkParentNode)

		treeModel = DefaultTreeModel(root)
		setModel(treeModel)
		setCellRenderer(CellRenderer())
		setRootVisible(false)
		setShowsRootHandles(true)

		addMouseListener(object : MouseAdapter() {
			override fun mousePressed(e: MouseEvent) {
				val pressedNode = UiUtils.getTreeNodeUnderMouse(this@QuickTabsTree, e)
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (nodeClickAction(pressedNode)) {
						isFocusable = true
						requestFocus()
					}
				}
				if (SwingUtilities.isRightMouseButton(e)) {
					triggerRightClickAction(e)
				}
			}
		})

		addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				if (e.keyCode == KeyEvent.VK_ENTER) {
					nodeClickAction(getLastSelectedPathComponent())
				}
			}
		})

		loadSettings()

		fillOpenParentNode()
		fillPinParentNode()
		fillBookmarkParentNode()
	}

	private fun triggerRightClickAction(e: MouseEvent) {
		val treeNode = UiUtils.getTreeNodeUnderMouse(this, e)
		if (treeNode !is QuickTabsBaseNode) {
			return
		}
		val menu = treeNode.onTreePopupMenu(mainWindow)
		if (menu != null) {
			menu.show(e.getComponent(), e.getX(), e.getY())
		}
	}

	private fun nodeClickAction(pressedNode: Any?): Boolean {
		if (pressedNode == null) {
			return false
		}
		if (pressedNode is QuickTabsChildNode) {
			mainWindow.getTabsController().selectTab(pressedNode.jNode)
			return true
		}
		return false
	}

	private fun fillOpenParentNode() {
		mainWindow.getTabsController().openTabs.forEach { onTabOpen(it) }
	}

	private fun fillPinParentNode() {
		mainWindow.getTabsController().pinnedTabs.forEach { onTabPinChange(it) }
	}

	private fun fillBookmarkParentNode() {
		mainWindow.getTabsController().bookmarkedTabs.forEach { onTabBookmarkChange(it) }
	}

	private fun clearParentNode(parentNode: QuickTabsParentNode) {
		val childIndices = IntArray(parentNode.childCount)
		val objects = arrayOfNulls<Any>(parentNode.childCount)
		for (i in childIndices.indices) {
			childIndices[i] = i
			objects[i] = parentNode.getChildAt(i)
		}
		parentNode.removeAllNodes()
		treeModel.nodesWereRemoved(parentNode, childIndices, objects)
	}

	private fun addJNode(parentNode: QuickTabsParentNode, node: JNode) {
		if (parentNode.addJNode(node)) {
			treeModel.nodesWereInserted(parentNode, intArrayOf(parentNode.childCount - 1))
		}
	}

	private fun removeJNode(parentNode: QuickTabsParentNode, node: JNode) {
		val child = parentNode.getQuickTabsNode(node)
		if (child != null) {
			val removedIndex = parentNode.getIndex(child)
			if (parentNode.removeJNode(node)) {
				treeModel.nodesWereRemoved(parentNode, intArrayOf(removedIndex), arrayOf<Any?>(child))
			}
		}
	}

	override fun valueChanged(event: TreeSelectionEvent) {
		val selectedNode = getLastSelectedPathComponent() as? DefaultMutableTreeNode ?: return
		if (selectedNode is QuickTabsChildNode) {
			val jNode = selectedNode.jNode
			mainWindow.getTabsController().selectTab(jNode)
		}
	}

	fun loadSettings() {
		setFont(mainWindow.getSettings().codeFont)
	}

	fun dispose() {
		mainWindow.getTabsController().removeListener(this)
	}

	override fun onTabOpen(blueprint: TabBlueprint) {
		if (!blueprint.isHidden && blueprint.node.supportsQuickTabs()) {
			addJNode(openParentNode, blueprint.node)
		}
	}

	override fun onTabClose(blueprint: TabBlueprint) {
		removeJNode(openParentNode, blueprint.node)
		removeJNode(pinParentNode, blueprint.node)
		removeJNode(bookmarkParentNode, blueprint.node)
	}

	override fun onTabPinChange(blueprint: TabBlueprint) {
		val node = blueprint.node
		if (blueprint.isPinned) {
			addJNode(pinParentNode, node)
		} else {
			removeJNode(pinParentNode, node)
		}
	}

	override fun onTabBookmarkChange(blueprint: TabBlueprint) {
		val node = blueprint.node
		if (blueprint.isBookmarked) {
			addJNode(bookmarkParentNode, node)
		} else {
			removeJNode(bookmarkParentNode, node)
		}
	}

	override fun onTabVisibilityChange(blueprint: TabBlueprint) {
		val node = blueprint.node
		if (!blueprint.isHidden) {
			addJNode(openParentNode, node)
		} else {
			removeJNode(openParentNode, node)
		}
	}

	private inner class Root : DefaultMutableTreeNode()

	private inner class CellRenderer : DefaultTreeCellRenderer() {
		override fun getTreeCellRendererComponent(
			tree: JTree,
			value: Any?,
			sel: Boolean,
			expanded: Boolean,
			leaf: Boolean,
			row: Int,
			hasFocus: Boolean,
		): Component {
			val c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
			if (value is QuickTabsBaseNode) {
				setIcon(value.getIcon())
			}
			return c
		}
	}
}
