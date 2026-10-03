package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode

/**
 * 遍历时针对某个块的“游标”信息。
 *
 * **含义**：反向遍历一个块内的指令时，我们需要记录已经跳过/已匹配的指令数量：
 * - [bottomOffset]：从块尾向上已处理的指令数（对应 Java 反编译中的“底部”）；
 * - [topOffset]：从块首向下已处理的指令数；
 * - [bottomImplicitCount]：块尾被识别为“隐式指令”（如 GOTO）的数量。
 *
 * 这些偏移都是**指令下标**而非指令字节大小。
 *
 * **Kotlin 转换说明**：
 * - 本类不是图节点（只是普通值对象），但保留原有 `duplicate()` 语义；
 * - `bottomImplicitCount` 的 getter 是 `getBottomImplicitCount()`，而 setter 原 Java 名为
 *   `setBottomImplicitOffset()`，名称不一致，无法用 Kotlin 属性表达，故用
 *   “私有字段 + 显式 getter/setter”。
 */
class TraverserBlockInfo(
	val block: BlockNode,
	var bottomOffset: Int,
	var topOffset: Int,
	bottomImplicitCount: Int,
) {

	private var bottomImplicitCountValue: Int = bottomImplicitCount

	constructor(block: BlockNode) : this(block, 0, 0, 0)

	override fun toString(): String = toString("")

	fun toString(indent: String): String {
		val sb = StringBuilder("BlockInsnInfo - ")
		sb.append(block.toString())
		sb.append(" [↑ ")
		sb.append(bottomOffset)
		sb.append("] [↓ ")
		sb.append(topOffset)
		sb.append("] ")
		return sb.toString()
	}

	fun duplicate(): TraverserBlockInfo = TraverserBlockInfo(block, bottomOffset, topOffset, bottomImplicitCountValue)

	val bottomImplicitCount: Int get() = bottomImplicitCountValue

	fun setBottomImplicitOffset(bottomImplicitCount: Int) {
		this.bottomImplicitCountValue = bottomImplicitCount
	}

	/**
	 * 取本块“尚未处理”的指令切片。
	 *
	 * 起点是 [topOffset]，终点是 `size - bottomOffset`；若跳过数量超过块内指令数则抛出越界异常，
	 * 与原 Java 行为一致。
	 */
	val insnsSlice: List<InsnNode> get() {
		val insns = block.instructions
		val totalSkippedCount = bottomOffset + topOffset
		if (totalSkippedCount > insns.size) {
			throw IndexOutOfBoundsException(
				"Attempted to get instructions slice of block " + block.toString() + " with " +
					totalSkippedCount + " skipped instructions whilst only having " + insns.size + " instructions in block.",
			)
		}
		val startIndex = topOffset
		val endIndex = insns.size - bottomOffset
		return insns.subList(startIndex, endIndex)
	}
}
