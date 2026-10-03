package jadx.gui.plugins.context

import jadx.api.gui.tree.ITreeNode
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.JMenuItem

/**
 * 树节点右键菜单的一项。
 *
 * **做什么**：保存菜单标题、是否添加的判定 [addPredicate] 以及点击动作；
 * [buildEntry] 在判定通过时创建 [JMenuItem]，否则返回 `null`。
 *
 * **为什么保持 Java 可实现**：这是插件扩展点，使用标准的 `Predicate` / `Consumer`。
 */
class TreePopupMenuEntry(
	private val name: String,
	private val addPredicate: Predicate<ITreeNode>,
	private val action: Consumer<ITreeNode>,
) {

	/** 为给定节点构建菜单项；判定不通过时返回 `null`。 */
	fun buildEntry(node: ITreeNode): JMenuItem? {
		if (!addPredicate.test(node)) {
			return null
		}
		val menuItem = JMenuItem(name)
		menuItem.addActionListener { action.accept(node) }
		return menuItem
	}
}
