package jadx.gui.ui.action

import jadx.api.JavaMethod
import jadx.core.codegen.TypeGen
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.StringUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JField
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.ui.dialog.MethodsDialog
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * 生成 Frida 注入脚本片段并复制到剪贴板的动作。
 *
 * **做什么**：对方法/字段直接生成对应 hook 代码；对类弹出方法选择对话框，
 * 为选中的每个方法生成 hook 片段。
 *
 * **线程模型**：类选择对话框通过 [SwingUtilities.invokeLater] 回到 EDT 打开。
 */
class FridaAction(codeArea: CodeArea) : JNodeAction(ActionModel.FRIDA_COPY, codeArea) {

	override fun runAction(node: JNode) {
		try {
			generateFridaSnippet(node)
		} catch (e: Exception) {
			LOG.error("Failed to generate Frida code snippet", e)
			JOptionPane.showMessageDialog(
				getCodeArea().mainWindow,
				e.localizedMessage,
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	override fun isActionEnabled(node: JNode?): Boolean = node is JMethod || node is JClass || node is JField

	private fun generateFridaSnippet(node: JNode) {
		val fridaSnippet: String
		if (node is JMethod) {
			fridaSnippet = generateMethodSnippet(node)
			copySnipped(fridaSnippet)
		} else if (node is JField) {
			fridaSnippet = generateFieldSnippet(node)
			copySnipped(fridaSnippet)
		} else if (node is JClass) {
			SwingUtilities.invokeLater { showMethodSelectionDialog(node) }
		} else {
			throw JadxRuntimeException("Unsupported node type: " + node.javaClass)
		}
	}

	private fun copySnipped(fridaSnippet: String) {
		if (!StringUtils.isEmpty(fridaSnippet)) {
			LOG.info("Frida snippet:\n{}", fridaSnippet)
			UiUtils.copyToClipboard(fridaSnippet)
		}
	}

	private fun generateMethodSnippet(jMth: JMethod): String {
		val classSnippet = generateClassSnippet(jMth.getJParent())
		val methodSnippet = getMethodSnippet(jMth.javaMethod, jMth.getJParent())
		return "$classSnippet\n$methodSnippet"
	}

	private fun generateMethodSnippet(javaMethod: JavaMethod, jc: JClass): String = getMethodSnippet(javaMethod, jc)

	private fun getMethodSnippet(javaMethod: JavaMethod, jc: JClass): String {
		val mth = javaMethod.getMethodNode()
		val methodInfo = mth.getMethodInfo()
		val methodName: String
		val newMethodName: String
		if (methodInfo.isConstructor()) {
			methodName = "\$init"
			newMethodName = methodName
		} else {
			methodName = StringEscapeUtils.escapeEcmaScript(methodInfo.name)
			newMethodName = StringEscapeUtils.escapeEcmaScript(methodInfo.alias)
		}
		val overload: String
		if (isOverloaded(mth)) {
			val overloadArgs = methodInfo.argumentsTypes.joinToString(", ") { parseArgType(it) }
			overload = ".overload($overloadArgs)"
		} else {
			overload = ""
		}
		val argNames = mth.collectArgNodes().map { it.getName() }
		val args = argNames.joinToString(", ")
		val logArgs: String
		if (argNames.isEmpty()) {
			logArgs = ""
		} else {
			logArgs = ": " + argNames.joinToString(", ") { arg -> "$arg=\${$arg}" }
		}
		val shortClassName = mth.parentClass.alias
		if (methodInfo.isConstructor() || methodInfo.returnType === ArgType.VOID) {
			// 无返回值：直接调用
			return shortClassName + "[\"" + methodName + "\"]" + overload + ".implementation = function (" + args + ") {\n" +
				"    console.log(`" + shortClassName + "." + newMethodName + " is called" + logArgs + "`);\n" +
				"    this[\"" + methodName + "\"](" + args + ");\n" +
				"};"
		}
		return shortClassName + "[\"" + methodName + "\"]" + overload + ".implementation = function (" + args + ") {\n" +
			"    console.log(`" + shortClassName + "." + newMethodName + " is called" + logArgs + "`);\n" +
			"    let result = this[\"" + methodName + "\"](" + args + ");\n" +
			"    console.log(`" + shortClassName + "." + newMethodName + " result=\${result}`);\n" +
			"    return result;\n" +
			"};"
	}

	private fun generateClassSnippet(jc: JClass): String {
		val javaClass = jc.getCls()
		val rawClassName = StringEscapeUtils.escapeEcmaScript(javaClass.getRawName())
		val shortClassName = javaClass.getName()
		return "var $shortClassName = Java.use(\"$rawClassName\");"
	}

	private fun showMethodSelectionDialog(jc: JClass) {
		val javaClass = jc.getCls()
		MethodsDialog(getCodeArea().mainWindow, javaClass.getMethods()) { result ->
			val fridaSnippet = generateClassAllMethodSnippet(jc, result)
			copySnipped(fridaSnippet)
		}
	}

	private fun generateClassAllMethodSnippet(jc: JClass, methodList: List<JavaMethod>): String {
		val result = StringBuilder()
		val classSnippet = generateClassSnippet(jc)
		result.append(classSnippet).append("\n")
		for (javaMethod in methodList) {
			result.append(generateMethodSnippet(javaMethod, jc)).append("\n")
		}
		return result.toString()
	}

	private fun generateFieldSnippet(jf: JField): String {
		val javaField = jf.javaField
		var rawFieldName = StringEscapeUtils.escapeEcmaScript(javaField.getRawName())
		val fieldName = javaField.getName()

		val methodNodes = javaField.getFieldNode().parentClass.methods
		for (methodNode in methodNodes) {
			if (methodNode.name == rawFieldName) {
				rawFieldName = "_$rawFieldName"
				break
			}
		}
		val jc = jf.getRootClass()
		val classSnippet = generateClassSnippet(jc)
		return "$classSnippet\n$fieldName = ${jc.getName()}.$rawFieldName.value;"
	}

	/** 判断方法是否与同类中其它方法重名（需要 `.overload(...)` 区分）。 */
	fun isOverloaded(methodNode: MethodNode): Boolean = methodNode.parentClass.methods.any { m ->
		m.name == methodNode.name &&
			methodNode.getMethodInfo().shortId != m.getMethodInfo().shortId
	}

	private fun parseArgType(x: ArgType): String {
		val typeStr: String
		if (x.isArray()) {
			typeStr = TypeGen.signature(x).replace("/", ".")
		} else {
			typeStr = x.toString()
		}
		return "'$typeStr'"
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FridaAction::class.java)
		private const val serialVersionUID = -3084073927621269039L
	}
}
