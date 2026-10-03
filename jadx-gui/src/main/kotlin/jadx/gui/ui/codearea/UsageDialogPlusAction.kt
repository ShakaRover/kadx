package jadx.gui.ui.codearea

import jadx.gui.treemodel.JNode
import jadx.gui.ui.action.ActionModel
import jadx.gui.ui.action.JNodeAction
import jadx.gui.ui.dialog.UsageDialogPlus

/**
 * 代码区右键菜单里的“查找用法（增强版）”动作。
 *
 * **做什么**：把菜单点击转发给 [UsageDialogPlus] 对话框，展示所选节点的引用列表。
 * **为什么继承 [JNodeAction]**：这类动作会随鼠标下的节点变化而启用/禁用，
 * [JNodeAction] 已经封装了该逻辑。
 */
class UsageDialogPlusAction(codeArea: CodeArea) : JNodeAction(ActionModel.FIND_USAGE_PLUS, codeArea) {

	override fun runAction(node: JNode) {
		UsageDialogPlus.open(getCodeArea().getMainWindow(), node)
	}

	companion object {
		private const val serialVersionUID = 4692546569977976384L
	}
}
