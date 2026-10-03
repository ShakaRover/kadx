package jadx.gui.ui.action

import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JRenameNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.dialog.RenameDialog

/**
 * 重命名动作。
 *
 * **做什么**：仅当节点实现 [JRenameNode] 且允许重命名时启用，触发后打开重命名对话框。
 */
class RenameAction(codeArea: CodeArea) : JNodeAction(ActionModel.CODE_RENAME, codeArea) {

	override fun isActionEnabled(node: JNode?): Boolean {
		if (node == null) {
			return false
		}
		return if (node is JRenameNode) node.canRename() else false
	}

	override fun runAction(node: JNode) {
		RenameDialog.rename(getCodeArea().getMainWindow(), node as JRenameNode)
	}

	companion object {
		private const val serialVersionUID = -4680872086148463289L
	}
}
