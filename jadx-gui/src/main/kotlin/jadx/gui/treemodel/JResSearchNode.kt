package jadx.gui.treemodel

import jadx.core.utils.StringUtils
import javax.swing.Icon

/**
 * 资源搜索结果节点。
 *
 * **做什么**：在资源搜索中把“命中的资源”与“命中的文本片段”绑定，用于搜索结果列表展示。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class JResSearchNode(
	private val resNode: JResource,
	private val text: String,
	private val pos: Int,
) : JNode() {

	fun getResNode(): JResource = resNode

	override fun getPos(): Int = pos

	override fun makeDescString(): String = text

	override fun getJParent(): JClass? = resNode.getJParent()

	override fun getName(): String? = resNode.getName()

	override fun makeString(): String = resNode.makeString()

	override fun makeLongString(): String = resNode.makeLongString()

	override fun makeLongStringHtml(): String = resNode.makeLongStringHtml()

	override fun getTooltip(): String? = resNode.getTooltip()

	override fun disableHtml(): Boolean = resNode.disableHtml()

	override fun getIcon(): Icon? = resNode.getIcon()

	override fun hasDescString(): Boolean = !StringUtils.isEmpty(text)

	companion object {
		private const val serialVersionUID = -2222084945157778639L
	}
}
