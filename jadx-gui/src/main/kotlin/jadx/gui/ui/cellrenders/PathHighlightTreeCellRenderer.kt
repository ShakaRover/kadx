package jadx.gui.ui.cellrenders

import jadx.gui.treemodel.JNode
import jadx.gui.utils.UiUtils
import java.awt.Color
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreePath

/**
 * 带“选中路径高亮”的树渲染器。
 *
 * **做什么**：为不同层级的节点计算不同的背景色；若节点位于当前选中路径上
 * （但不是选中节点本身），则用高亮前景色；节点若是 [JNode] 则替换文本/图标/提示。
 *
 * **为什么保留 `DefaultTreeCellRenderer` 的覆写签名**：`JTree` 会按该虚方法回调。
 */
class PathHighlightTreeCellRenderer : DefaultTreeCellRenderer() {

	private val isDarkTheme: Boolean

	init {
		val themeBackground = UIManager.getColor("Panel.background")
		isDarkTheme = UiUtils.isDarkTheme(themeBackground)
	}

	override fun getTreeCellRendererComponent(
		tree: JTree,
		value: Any?,
		selected: Boolean,
		expanded: Boolean,
		leaf: Boolean,
		row: Int,
		hasFocus: Boolean,
	): Component {
		val comp = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

		val node = value as DefaultMutableTreeNode
		val userObject = node.userObject

		// 计算节点层级，并按层级生成一个柔和的背景色
		val level = node.level
		val hue = (level * 0.1f) % 1.0f // 色相循环
		val levelColor = Color.getHSBColor(hue, 0.1f, 0.95f)

		// 判断该节点是否在当前选中路径上
		var onSelectionPath = false
		val selectionPath: TreePath? = tree.selectionPath
		if (selectionPath != null) {
			val selectedPathNodes = selectionPath.path
			for (pathNode in selectedPathNodes) {
				if (pathNode === node) {
					onSelectionPath = true
					break
				}
			}
		}

		if (onSelectionPath && !selected) {
			// 在选中路径上但不是选中节点：使用特殊前景色
			foreground = if (isDarkTheme) Color.decode("#70AEFF") else Color.decode("#0033B3")
		} else if (!selected) {
			// 未选中时才应用层级背景色
			background = levelColor
			border = BorderFactory.createEmptyBorder(2, level * 2 + 1, 2, 1)
		} else {
			// 选中节点同样补一个缩进边框
			border = BorderFactory.createEmptyBorder(2, level * 2 + 1, 2, 1)
		}

		if (userObject is JNode) {
			text = userObject.makeLongString()
			icon = userObject.getIcon()
			toolTipText = userObject.getTooltip()
		}
		return comp
	}
}
