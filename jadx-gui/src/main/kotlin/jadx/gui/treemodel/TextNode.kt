package jadx.gui.treemodel

import javax.swing.Icon

/**
 * 纯文本占位节点（例如“加载中…”提示）。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class TextNode(private val label: String) : JNode() {

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon? = null

	override fun makeString(): String = label

	companion object {
		private const val serialVersionUID = 2342749142368352232L
	}
}
