package jadx.core.dex.attributes.nodes

import jadx.core.dex.attributes.AttrNode
import jadx.core.dex.attributes.ILineAttributeNode

/**
 * 带行号信息的属性节点基类，是 [AttrNode] 的常用子类。
 *
 * **两个位置概念**：
 * - [sourceLine]：原始源码中的行号（0 表示未知）；
 * - [defPosition]：在反编译生成代码中的字符偏移（声明位置），供 UI 定位。
 *
 * **Kotlin 转换说明**：字段私有 + 显式 `getXxx/setXxx` 覆写 [ILineAttributeNode]，
 * 避免 Kotlin 属性生成的 getter 与接口方法签名产生歧义。
 */
abstract class LineAttrNode :
	AttrNode(),
	ILineAttributeNode {

	private var sourceLine: Int = 0

	/** 节点在反编译代码中声明位置的字符偏移 */
	private var defPosition: Int = 0

	override fun getSourceLine(): Int = sourceLine

	override fun setSourceLine(sourceLine: Int) {
		this.sourceLine = sourceLine
	}

	override fun getDefPosition(): Int = this.defPosition

	override fun setDefPosition(defPosition: Int) {
		this.defPosition = defPosition
	}

	/** 若本节点还没有源码行号，则从另一个节点继承 */
	open fun addSourceLineFrom(lineAttrNode: LineAttrNode) {
		if (this.getSourceLine() == 0) {
			this.setSourceLine(lineAttrNode.getSourceLine())
		}
	}

	/** 从另一个节点完整拷贝行号与声明位置 */
	open fun copyLines(lineAttrNode: LineAttrNode) {
		setSourceLine(lineAttrNode.getSourceLine())
		setDefPosition(lineAttrNode.getDefPosition())
	}
}
