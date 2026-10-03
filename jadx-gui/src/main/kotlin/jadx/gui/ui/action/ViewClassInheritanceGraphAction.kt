package jadx.gui.ui.action

import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JField
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.graphs.ClassInheritanceGraphDialog
import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane

/**
 * 查看类继承图的动作。
 *
 * **做什么**：把方法/字段/类节点统一归一为其所属 [JClass]，再打开
 * [ClassInheritanceGraphDialog]。
 */
class ViewClassInheritanceGraphAction(codeArea: CodeArea) : JNodeAction(ActionModel.VIEW_CLASS_INHERITANCE_GRAPH, codeArea) {

	override fun isActionEnabled(node: JNode?): Boolean = node is JMethod || node is JClass || node is JField

	override fun runAction(node: JNode) {
		try {
			val classNode: JClass
			if (node is JMethod) {
				classNode = node.getJParent()
			} else if (node is JField) {
				classNode = node.getJParent()
			} else if (node is JClass) {
				classNode = node
			} else {
				throw JadxRuntimeException("Unsupported node type: " + node.javaClass)
			}
			ClassInheritanceGraphDialog.open(getCodeArea().mainWindow, classNode)
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
		private val LOG: Logger = LoggerFactory.getLogger(ViewClassInheritanceGraphAction::class.java)
		private const val serialVersionUID = -331826691076655264L
	}
}
