package jadx.gui.plugins.context

import jadx.api.gui.tree.ITreeNode
import javax.swing.JMenuItem

/**
 * 树节点右键菜单的一项。
 *
 * **做什么**：保存菜单标题、是否添加的判定 [addPredicate] 以及点击动作；
 * [buildEntry] 在判定通过时创建 [JMenuItem]，否则返回 `null`。
 *
 * **为什么这样写**：内部数据持有者，回调用 Kotlin 函数类型；插件 API 侧仍在 `JadxGuiContext` 中使用 Java 类型。
 */
class TreePopupMenuEntry(
	private val name: String,
	private val addPredicate: (ITreeNode) -> Boolean,
	private val action: (ITreeNode) -> Unit,
) {

	/** 为给定节点构建菜单项；判定不通过时返回 `null`。 */
	fun buildEntry(node: ITreeNode): JMenuItem? {
		if (!addPredicate(node)) {
			return null
		}
		val menuItem = JMenuItem(name)
		menuItem.addActionListener { action(node) }
		return menuItem
	}
}
