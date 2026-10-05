package kadx.gui.ui.action

import kadx.gui.treemodel.JNode
import kadx.gui.ui.codearea.CodeArea
import kadx.gui.ui.dialog.UsageDialog

/**
 * 查找用法的动作。
 *
 * **做什么**：把选中节点交给 [UsageDialog]，展示其被引用的位置列表。
 */
class FindUsageAction(codeArea: CodeArea) : JNodeAction(ActionModel.FIND_USAGE, codeArea) {

	override fun runAction(node: JNode) {
		UsageDialog.open(getCodeArea().mainWindow, node)
	}

	companion object {
		private const val serialVersionUID = 4692546569977976384L
	}
}
