package jadx.gui.ui.action

import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.graphs.CallGraphDialog
import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane

/**
 * 查看方法调用图的动作。
 *
 * **做什么**：对方法节点打开 [CallGraphDialog]；仅在节点为方法时可用。
 */
class ViewCallGraphAction(codeArea: CodeArea) : JNodeAction(ActionModel.VIEW_CALL_GRAPH, codeArea) {

	override fun isActionEnabled(node: JNode?): Boolean = node is JMethod

	override fun runAction(node: JNode) {
		try {
			CallGraphDialog.open(getCodeArea().getMainWindow(), node as JMethod)
		} catch (e: Exception) {
			LOG.error("Failed to view graph", e)
			JOptionPane.showMessageDialog(
				getCodeArea().getMainWindow(),
				e.localizedMessage,
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ViewCallGraphAction::class.java)
		private const val serialVersionUID = -11122327621269039L
	}
}
