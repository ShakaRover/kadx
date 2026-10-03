package jadx.gui.ui.action

import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea
import java.awt.event.ActionEvent

/**
 * 针对代码区中 [JNode]（类/方法/字段等）的动作基类。
 *
 * **做什么**：维护“当前光标下节点”，并根据节点类型启用/禁用自身；
 * 触发时把节点交给子类实现的 [runAction]。
 *
 * **线程模型**：保持 Swing 原样，动作在 EDT 上执行。
 */
abstract class JNodeAction : CodeAreaAction {
	/** 当前关联的节点；未绑定时为 null。 */
	private var node: JNode? = null

	constructor(actionModel: ActionModel, codeArea: CodeArea) : super(actionModel, codeArea)

	constructor(id: String, codeArea: CodeArea) : super(id, codeArea)

	/** 子类实现的具体动作。 */
	abstract fun runAction(node: JNode)

	/** 该动作是否对给定节点可用；默认只要节点非空即可用。 */
	open fun isActionEnabled(node: JNode?): Boolean = node != null

	override fun actionPerformed(e: ActionEvent) {
		if (JadxGuiAction.isSource(e)) {
			// 由快捷键/菜单触发：实时取光标下节点
			val nodeUnderCaret = codeArea?.getNodeUnderCaret()
			node = nodeUnderCaret
			if (isActionEnabled(nodeUnderCaret)) {
				runAction(checkNotNull(nodeUnderCaret))
			}
		} else {
			// 由右键菜单等直接触发：使用已绑定的节点
			runAction(checkNotNull(node))
		}
	}

	/** 切换当前节点，并据此刷新启用状态。 */
	fun changeNode(node: JNode?) {
		this.node = node
		setEnabled(isActionEnabled(node))
	}

	override fun dispose() {
		super.dispose()
		node = null
		for (changeListener in getPropertyChangeListeners()) {
			removePropertyChangeListener(changeListener)
		}
	}
}
