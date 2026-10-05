package kadx.gui.ui.action

import kadx.gui.treemodel.JNode
import kadx.gui.ui.codearea.CodeArea

/**
 * 跳转到声明的动作。
 *
 * **做什么**：调用标签页控制器的 `codeJump`，把光标跳转到节点声明处。
 */
class GoToDeclarationAction(codeArea: CodeArea) : JNodeAction(ActionModel.GOTO_DECLARATION, codeArea) {

	override fun runAction(node: JNode) {
		getCodeArea().getContentPanel().tabsController.codeJump(node)
	}

	companion object {
		private const val serialVersionUID = -1186470538894941301L
	}
}
