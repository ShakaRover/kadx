package jadx.gui.ui.action

import com.google.gson.Gson
import com.google.gson.JsonParser
import jadx.core.utils.GsonUtils
import jadx.gui.treemodel.JNode
import jadx.gui.ui.codearea.CodeArea

/**
 * 美化（格式化）JSON 的动作。
 *
 * **做什么**：读取代码区当前文本，按 JSON 解析后用 Gson 重新序列化为带缩进的格式。
 */
class JsonPrettifyAction(codeArea: CodeArea) : JNodeAction(ActionModel.JSON_PRETTIFY, codeArea) {

	override fun runAction(node: JNode) {
		val originString = getCodeArea().getCodeInfo().getCodeStr()
		val je = JsonParser.parseString(originString)
		val prettyString = GSON.toJson(je)
		getCodeArea().setText(prettyString)
	}

	override fun isActionEnabled(node: JNode?): Boolean = true

	companion object {
		private const val serialVersionUID = -2682529369671695550L
		private val GSON: Gson = GsonUtils.buildGson()
	}
}
