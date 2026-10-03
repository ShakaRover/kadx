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
 * **Kotlin 转换说明**：字段私有 + Kotlin 属性覆写 [ILineAttributeNode] 的只读 getter，
 * 写入仍用显式 `setSourceLine/setDefPosition`，JVM 方法名与原来一致。
 */
abstract class LineAttrNode :
	AttrNode(),
	ILineAttributeNode {

	private var sourceLineValue: Int = 0

	/** 节点在反编译代码中声明位置的字符偏移 */
	private var defPositionValue: Int = 0

	override val sourceLine: Int get() = sourceLineValue

	override fun setSourceLine(sourceLine: Int) {
		this.sourceLineValue = sourceLine
	}

	override val defPosition: Int get() = defPositionValue

	override fun setDefPosition(defPosition: Int) {
		this.defPositionValue = defPosition
	}

	/** 若本节点还没有源码行号，则从另一个节点继承 */
	open fun addSourceLineFrom(lineAttrNode: LineAttrNode) {
		if (this.sourceLine == 0) {
			this.setSourceLine(lineAttrNode.sourceLine)
		}
	}

	/** 从另一个节点完整拷贝行号与声明位置 */
	open fun copyLines(lineAttrNode: LineAttrNode) {
		setSourceLine(lineAttrNode.sourceLine)
		setDefPosition(lineAttrNode.defPosition)
	}
}
