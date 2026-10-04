package jadx.gui.ui.action

import jadx.api.JavaClass
import jadx.api.JavaField
import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.core.codegen.TypeGen
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JField
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 复制节点的 smali 原始引用（PR #2955）。
 *
 * **做什么**：按 smali 语法拼出“原始（未重命名）”的类/方法/字段引用并写入剪贴板，
 * 例如 `Lcom/example/Cls;->method(I[Ljava/lang/String;)V`。
 *
 * **为什么使用原始名**：反混淆/重命名后的名字无法用于 smali 工具链，
 * 这里始终读取 [ClassInfo]/[MethodInfo]/[FieldInfo] 的原始类型签名。
 */
class CopySmaliReferenceAction(codeArea: CodeArea) : JNodeAction(ActionModel.COPY_SMALI_REFERENCE, codeArea) {

	override fun runAction(node: JNode) {
		val javaNode: JavaNode? = node.getJavaNode()
		val ref: String
		if (javaNode is JavaClass) {
			ref = getSmaliReference(javaNode.getClassNode().classInfo)
		} else if (javaNode is JavaMethod) {
			ref = getSmaliReference(javaNode.getMethodNode().methodInfo)
		} else if (javaNode is JavaField) {
			ref = getSmaliReference(javaNode.getFieldNode().getFieldInfo())
		} else {
			LOG.warn("Copy smali reference not supported for node type: {}", node.javaClass)
			return
		}
		UiUtils.copyToClipboard(ref)
	}

	override fun isActionEnabled(node: JNode?): Boolean = node is JMethod || node is JClass || node is JField

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CopySmaliReferenceAction::class.java)
		private const val serialVersionUID = 3504906924823485163L

		@JvmStatic
		fun getSmaliReference(cls: ClassInfo): String = TypeGen.signature(cls.type)

		@JvmStatic
		fun getSmaliReference(mth: MethodInfo): String = getSmaliReference(mth.declClass) + "->" + mth.shortId

		@JvmStatic
		fun getSmaliReference(fld: FieldInfo): String = getSmaliReference(fld.declClass) + "->" + fld.name + ':' + TypeGen.signature(fld.type)
	}
}
