package kadx.core.dex.visitors.finaly.traverser.visitors

import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.visitors.finaly.CentralityState
import kadx.core.dex.visitors.finaly.traverser.state.AwaitingInsnCompareTraverserState
import kadx.core.dex.visitors.finaly.traverser.state.NoBlockTraverserState
import kadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import kadx.core.dex.visitors.finaly.traverser.state.TraverserState

/**
 * “路径结尾”访问器：识别块尾的 RETURN/THROW 以及为它们准备返回值的指令。
 *
 * **背景**：候选分支尾部常常混有正常 return/throw 的收尾指令，以及 finally 复制出来的指令。
 * 本访问器把块尾的“路径结束指令”及其依赖的寄存器参数标记为“允许的输出”
 * （记录进 [CentralityState]），从而在后续反向比较时能够正确跳过这些收尾代码。
 *
 * **Kotlin 转换说明**：静态方法 [isInstructionPathEnd] 放入 `companion object` + `@JvmStatic`。
 */
class PathEndBlockTraverserVisitor(state: TraverserState) : AbstractBlockTraverserVisitor(state) {

	override fun visit(block: BlockNode): TraverserState {
		val centralityState: CentralityState = getState().centralityState
		val insnInfo = getState().getBlockInsnInfo()
		if (!centralityState.allowsCentral) {
			return AwaitingInsnCompareTraverserState(getComparator(), centralityState, checkNotNull(insnInfo))
		}
		val validInsnInfo: TraverserBlockInfo = checkNotNull(insnInfo)
		val insns: List<InsnNode> = validInsnInfo.insnsSlice
		val insnsIterator = insns.listIterator(insns.size)

		// 统计块尾被识别为“路径结束相关”的指令数量。
		var bottomDelta = 0
		while (insnsIterator.hasPrevious()) {
			val insn = insnsIterator.previous()

			// 判断该指令是否属于“路径结束”指令，若是则忽略它本身。
			if (isInstructionPathEnd(insn)) {
				// 路径结束指令会导致处理器退出。这里检查它的参数：
				// 若为 THROW/RETURN 且第一个参数是寄存器，则该寄存器在本作用域退出前被使用，
				// 因此把它标记为“允许的输出”。
				//
				// 例如：
				//   CONST_STR r2 = "return this string"  <-- 路径结束指令（设置 RETURN 使用的参数）
				//   RETURN r2                             <-- 路径结束指令
				if (insn.argsCount != 0) {
					val handlerExitArg = insn.getArg(0)
					// 指令返回值只能是寄存器参数，因此确认它是 RegisterArg。
					if (handlerExitArg is RegisterArg) {
						centralityState.addAllowableOutput(handlerExitArg)
					}
				}

				bottomDelta++
			} else if (centralityState.hasAllowableOutput(insn)) {
				// 若该指令不是路径结束指令，但它的结果被路径结束指令使用，则同样跳过并登记其参数。
				bottomDelta++
				centralityState.addAllowableOutputs(insn)
			} else {
				break
			}
		}

		validInsnInfo.bottomOffset = validInsnInfo.bottomOffset + bottomDelta

		val sourceBlock: BlockNode = validInsnInfo.block
		val noInstructionsLeft = validInsnInfo.bottomOffset >= sourceBlock.instructions.size
		return if (noInstructionsLeft) {
			// 该块已无剩余指令：标记状态去查找前驱，继续搜索重复指令。
			NoBlockTraverserState(getComparator(), centralityState, sourceBlock)
		} else {
			// 还有剩余指令：标记状态等待指令比较。
			AwaitingInsnCompareTraverserState(getComparator(), centralityState, validInsnInfo)
		}
	}

	companion object {
		/** 判断一条指令是否为“路径结束”指令（RETURN 或 THROW）。 */
		fun isInstructionPathEnd(insn: InsnNode): Boolean {
			val type: InsnType = insn.type

			return when (type) {
				InsnType.RETURN, InsnType.THROW -> true
				else -> false
			}
		}
	}
}
