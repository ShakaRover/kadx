package kadx.gui.treemodel

import kadx.api.JavaNode
import javax.swing.Icon

/**
 * 搜索结果里的“代码位置”节点。
 *
 * **做什么**：把某个 [JNode]（通常是方法/字段）与它出现的一行代码绑定起来，
 * 用于搜索结果列表中显示“匹配到的源码行”，点击后跳转到该位置。
 *
 * **为什么不是 `data class`**：节点相等性委托给内部 [jNode]，且需要按身份参与树比较。
 */
class CodeNode(
	private val rootCls: JClass,
	private val jNode: JNode,
	private val line: String,
	private val pos: Int,
) : JNode() {

	override fun getIcon(): Icon? = jNode.getIcon()

	override fun getJavaNode(): JavaNode? = jNode.getJavaNode()

	override fun getJParent(): JClass = getRootClass()

	override fun getRootClass(): JClass = rootCls

	override fun makeDescString(): String = line

	override fun hasDescString(): Boolean = true

	override fun makeString(): String = jNode.makeString()

	override fun makeStringHtml(): String = jNode.makeStringHtml()

	override fun makeLongString(): String = jNode.makeLongString()

	override fun makeLongStringHtml(): String = jNode.makeLongStringHtml()

	override fun disableHtml(): Boolean = jNode.disableHtml()

	override fun getSyntaxName(): String? = jNode.getSyntaxName()

	override fun getPos(): Int = pos

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is CodeNode) {
			return false
		}
		return jNode == other.jNode
	}

	override fun hashCode(): Int = jNode.hashCode()

	override fun compareTo(other: JNode): Int {
		if (other is CodeNode) {
			return jNode.compareTo(other.jNode)
		}
		return super.compareTo(other)
	}

	companion object {
		private const val serialVersionUID = 1658650786734966545L
	}
}
