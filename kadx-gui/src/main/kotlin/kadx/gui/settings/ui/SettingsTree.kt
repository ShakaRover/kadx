package kadx.gui.settings.ui

import kadx.api.plugins.gui.ISettingsGroup
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.utils.NLS
import java.util.Collections
import java.util.Objects
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * 设置窗口左侧的树形导航。
 *
 * **做什么**：把 [ISettingsGroup] 列表递归构造成树，默认全部展开且禁止折叠根节点；
 * 选中某节点时通知 [KadxSettingsWindow] 切换右侧页面。
 */
class SettingsTree(private val settingsWindow: KadxSettingsWindow) : JTree() {

	fun init(groups: List<ISettingsGroup>) {
		val treeRoot = DefaultMutableTreeNode(NLS.str("preferences.title"))
		addGroups(treeRoot, groups)
		model = DefaultTreeModel(treeRoot)
		selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
		isFocusable = false
		addTreeSelectionListener { switchGroup() }
		// 展开所有节点并禁止折叠
		setNodeExpandedState(this, treeRoot, true)
		addTreeWillExpandListener(DisableRootCollapseListener(treeRoot))
		addSelectionRow(1)
	}

	private fun addGroups(base: DefaultMutableTreeNode, groups: List<ISettingsGroup>) {
		for (group in groups) {
			val node = SettingsTreeNode(group)
			base.add(node)
			addGroups(node, group.getSubGroups())
		}
	}

	fun selectGroup(group: ISettingsGroup) {
		val node = searchTreeNode(group)
			?: throw KadxRuntimeException("Settings group not found: $group")
		selectionPath = TreePath(node.path)
	}

	private fun searchTreeNode(group: ISettingsGroup): SettingsTreeNode? {
		val root = model.root as DefaultMutableTreeNode
		val enumeration = root.children()
		while (enumeration.hasMoreElements()) {
			val node = enumeration.nextElement() as SettingsTreeNode
			if (node.getGroup() === group) {
				return node
			}
		}
		return null
	}

	private fun switchGroup() {
		val selected = lastSelectedPathComponent
		if (selected is SettingsTreeNode) {
			val group = selected.getGroup()
			settingsWindow.activateGroup(group)
		} else {
			settingsWindow.activateGroup(null)
		}
	}

	private fun setNodeExpandedState(tree: JTree, node: TreeNode, expanded: Boolean) {
		val list: List<TreeNode> = Collections.list(node.children())
		for (treeNode in list) {
			setNodeExpandedState(tree, treeNode, expanded)
		}
		val mutableTreeNode = node as DefaultMutableTreeNode
		if (!expanded && mutableTreeNode.isRoot) {
			return
		}
		val path = TreePath(mutableTreeNode.path)
		if (expanded) {
			tree.expandPath(path)
		} else {
			tree.collapsePath(path)
		}
	}

	private class DisableRootCollapseListener(private val treeRoot: DefaultMutableTreeNode) : TreeWillExpandListener {

		override fun treeWillExpand(event: TreeExpansionEvent) {
			// 展开始终允许
		}

		override fun treeWillCollapse(event: TreeExpansionEvent) {
			val current = event.path.lastPathComponent
			if (Objects.equals(current, treeRoot)) {
				throw ExpandVetoException(event, "Root collapsing not allowed")
			}
		}
	}
}
