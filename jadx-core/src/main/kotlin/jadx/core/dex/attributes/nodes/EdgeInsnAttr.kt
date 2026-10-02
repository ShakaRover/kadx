package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.AttrList
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.Edge
import jadx.core.dex.nodes.InsnNode
import java.util.Objects

/**
 * “边上的指令”属性：把一条指令与基本块之间的一条边（[Edge]）关联起来。
 *
 * **用途**：区域还原时，有些指令逻辑上属于“从 start 块跳到 end 块”这条边
 * （例如 break/continue 插入的跳转），需要同时挂到边两端的块上，便于遍历。
 *
 * **相等性**：本类实现了基于内容的 [equals]（块相等 + 指令深度相等），
 * 因此同一对块上的重复边指令不会被重复添加。注意这是属性节点中少见的“值语义”类。
 */
class EdgeInsnAttr private constructor(
	val start: BlockNode,
	val end: BlockNode,
	val insn: InsnNode,
) : IJadxAttribute {

	companion object {
		/** 按 [Edge] 添加边指令 */
		@JvmStatic
		fun addEdgeInsn(edge: Edge, insn: InsnNode) {
			addEdgeInsn(edge.source, edge.target, insn)
		}

		/** 按起止块添加边指令（两端块都挂载，避免重复） */
		@JvmStatic
		fun addEdgeInsn(start: BlockNode, end: BlockNode, insn: InsnNode) {
			val edgeInsnAttr = EdgeInsnAttr(start, end, insn)
			if (!start.getAll(AType.EDGE_INSN).contains(edgeInsnAttr)) {
				start.addAttr(AType.EDGE_INSN, edgeInsnAttr)
			}
			if (!end.getAll(AType.EDGE_INSN).contains(edgeInsnAttr)) {
				end.addAttr(AType.EDGE_INSN, edgeInsnAttr)
			}
		}
	}

	override fun getAttrType(): AType<AttrList<EdgeInsnAttr>> = AType.EDGE_INSN

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val that = other as EdgeInsnAttr
		return start == that.start &&
			end == that.end &&
			insn.isDeepEquals(that.insn)
	}

	override fun hashCode(): Int = Objects.hash(start, end, insn)

	override fun toString(): String = "EDGE_INSN: $start->$end $insn"
}
