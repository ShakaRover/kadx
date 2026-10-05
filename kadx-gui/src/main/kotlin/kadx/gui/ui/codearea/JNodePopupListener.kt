package kadx.gui.ui.codearea

import kadx.gui.treemodel.JNode
import kadx.gui.ui.action.JNodeAction
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * 监听右键弹出菜单的显示/取消，并同步“当前节点”到菜单里的各个 [JNodeAction]。
 *
 * **做什么**：弹出菜单可能针对光标下的不同节点打开，菜单动作需要知道当前节点是谁。
 * 本监听器在菜单可见时把 [CodeArea.getNodeUnderMouse] 的结果推给动作，
 * 在菜单取消时清空节点。
 *
 * **为什么 `popupMenuWillBecomeInvisible` 不做处理**：该回调可能在动作真正执行之前
 * 就被调用，此时若重置节点会导致动作拿到空节点。
 */
class JNodePopupListener(private val codeArea: CodeArea) : PopupMenuListener {
	private val actions = ArrayList<JNodeAction>()

	/** 注册一个需要跟随节点变化的菜单动作。 */
	fun addActions(action: JNodeAction) {
		actions.add(action)
	}

	private fun updateNode(node: JNode?) {
		for (action in actions) {
			action.changeNode(node)
		}
	}

	override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
		updateNode(codeArea.nodeUnderMouse)
	}

	override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
		// 该事件可能在执行动作前触发，这里不能重置节点
	}

	override fun popupMenuCanceled(e: PopupMenuEvent) {
		updateNode(null)
	}
}
