package kadx.gui.ui.action

import kadx.api.JavaClass
import kadx.api.JavaField
import kadx.api.JavaMethod
import kadx.api.JavaNode
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JField
import kadx.gui.treemodel.JMethod
import kadx.gui.treemodel.JNode
import kadx.gui.ui.codearea.CodeArea
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 复制节点“引用”（全限定名）到剪贴板的动作。
 *
 * **做什么**：根据节点类型拼出引用字符串——类用全名，方法与字段用
 * `声明类全名.成员名`，然后写入系统剪贴板。
 */
class CopyReferenceAction(codeArea: CodeArea) : JNodeAction(ActionModel.COPY_REFERENCE, codeArea) {

	override fun runAction(node: JNode) {
		val javaNode: JavaNode? = node.getJavaNode()
		val ref: String
		if (javaNode is JavaClass) {
			ref = javaNode.getFullName()
		} else if (javaNode is JavaMethod) {
			ref = javaNode.declaringClass.getFullName() + '.' + javaNode.getName()
		} else if (javaNode is JavaField) {
			ref = javaNode.declaringClass.getFullName() + '.' + javaNode.getName()
		} else {
			LOG.warn("Copy reference not supported for node type: {}", node.javaClass)
			return
		}
		UiUtils.copyToClipboard(ref)
	}

	override fun isActionEnabled(node: JNode?): Boolean = node is JMethod || node is JClass || node is JField

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CopyReferenceAction::class.java)
		private const val serialVersionUID = -8816072267744391424L
	}
}
