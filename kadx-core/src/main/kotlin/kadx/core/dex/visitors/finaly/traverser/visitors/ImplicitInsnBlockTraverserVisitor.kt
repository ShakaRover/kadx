package kadx.core.dex.visitors.finaly.traverser.visitors

import kadx.core.dex.instructions.InsnType
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import kadx.core.dex.visitors.finaly.traverser.state.TraverserState

/**
 * “隐式指令”访问器：在反向遍历块内指令时，跳过那些可以安全忽略的指令。
 *
 * **为什么可以忽略**：GOTO 的跳转关系已经体现在块图结构中（GOTO 通常是单后继块的最后一条指令），
 * 因此比较指令时不需要再考虑它。NOP 之类的空操作同理。
 *
 * **Kotlin 转换说明**：原 Java 的静态工具方法 [isInstructionImplicit] 放入
 * `companion object` 并加 `@JvmStatic`，Java 调用方仍可写
 * `ImplicitInsnBlockTraverserVisitor.isInstructionImplicit(...)`。
 */
class ImplicitInsnBlockTraverserVisitor(state: TraverserState) : AbstractBlockTraverserVisitor(state) {

	override fun visit(block: BlockNode): TraverserState {
		val insnInfo: TraverserBlockInfo = checkNotNull(getState().getBlockInsnInfo())
		val insns: List<InsnNode> = insnInfo.insnsSlice
		val insnsIterator = insns.listIterator(insns.size)

		// 统计块尾被识别为“隐式指令”的数量。
		var bottomDelta = 0
		while (insnsIterator.hasPrevious()) {
			val insn = insnsIterator.previous()
			if (!isInstructionImplicit(insn)) {
				break
			}
			bottomDelta++
		}
		// 把游标向上移动，并累计隐式指令数（后者用于后续判断是否整块都已匹配）。
		insnInfo.bottomOffset = insnInfo.bottomOffset + bottomDelta
		insnInfo.setBottomImplicitOffset(insnInfo.bottomImplicitCount + bottomDelta)
		return getState()
	}

	companion object {
		/**
		 * 判断一条指令是否为“隐式指令”（可安全跳过）。
		 *
		 * 目前仅把 GOTO 视为隐式：它的跳转语义已经编码在块图里。
		 */
		fun isInstructionImplicit(node: InsnNode): Boolean {
			// 原 Java 注释：反向遍历比较时，若指令可以安全跳过，则称其为隐式指令。
			// GOTO 的存在应反映在块图结构中，因此这里跳过它。
			return node.type == InsnType.GOTO
		}
	}
}
