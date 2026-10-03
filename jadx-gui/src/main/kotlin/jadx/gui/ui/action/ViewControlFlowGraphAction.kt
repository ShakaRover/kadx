package jadx.gui.ui.action

import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.graphs.ControlFlowGraphDialog
import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane

/**
 * 查看方法控制流图（CFG）的动作。
 *
 * **做什么**：对方法节点打开 [ControlFlowGraphDialog]；仅在节点为方法时可用。
 */
class ViewControlFlowGraphAction(actionModel: ActionModel, codeArea: CodeArea) : JNodeAction(actionModel, codeArea) {

	override fun isActionEnabled(node: JNode?): Boolean = node is JMethod

	override fun runAction(node: JNode) {
		try {
			ControlFlowGraphDialog.open(getCodeArea().mainWindow, node as JMethod)
		} catch (e: Exception) {
			LOG.error("Failed to view graph", e)
			JOptionPane.showMessageDialog(
				getCodeArea().mainWindow,
				e.localizedMessage,
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ViewControlFlowGraphAction::class.java)
		private const val serialVersionUID = -490213655L
	}
}
