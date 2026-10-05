package kadx.core.dex.visitors

import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.RegDebugInfoAttr
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.dex.visitors.ssa.SSATransform
import kadx.core.utils.InsnRemover
import java.util.ArrayList

/**
 * 内联冗余的 `move` 指令。
 *
 * **做什么**：扫描每个基本块中的 `move`。若目标变量只用一次、且源头不是已合并进 phi 的变量，
 * 就把该 move 的源值直接替换到使用点，从而消除这次寄存器搬运（保留调试信息属性）。
 *
 * **为什么在 SSA 之后、CodeShrink 之前**：此时变量使用关系最清晰；先内联 move 能让后续
 * 代码收缩/表达式合并得到更简洁的结果。
 */
@KadxVisitor(
	name = "MoveInlineVisitor",
	desc = "Inline redundant move instructions",
	runAfter = [SSATransform::class],
	runBefore = [CodeShrinkVisitor::class],
)
class MoveInlineVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		moveInline(mth)
	}

	companion object {
		fun moveInline(mth: MethodNode) {
			val remover = InsnRemover(mth)
			for (block in checkNotNull(mth.basicBlocks)) {
				remover.setBlock(block)
				for (insn in block.instructions) {
					if (insn.type != InsnType.MOVE) {
						continue
					}
					if (processMove(mth, insn)) {
						remover.addAndUnbind(insn)
					}
				}
				remover.perform()
			}
		}

		private fun processMove(mth: MethodNode, move: InsnNode): Boolean {
			// 结果已被前面的优雅降级置空（如 unbind）时不内联
			val resultArg = move.result ?: return false
			val moveArg = move.getArg(0)
			if (resultArg.sameRegAndSVar(moveArg)) {
				return true
			}
			if (moveArg.isRegister) {
				val moveReg = moveArg as RegisterArg
				val moveSVar = moveReg.sVar
				if (moveSVar == null || moveSVar.isAssignInPhi()) {
					// 未绑定 SSA 的参数不内联；已合并的变量不要打乱
					return false
				}
			}
			// 结果寄存器未绑定 SSA（中途夭折重处理周期的残留）时不内联
			val ssaVar = resultArg.sVar ?: return false
			if (ssaVar.useList.isEmpty()) {
				// 结果未被使用
				return true
			}

			if (ssaVar.isUsedInPhi()) {
				return false
				// TODO: 重新审视 'up' move inline 的条件（见测试 TestMoveInline）
				// return deleteMove(mth, move)
			}
			var debugInfo: RegDebugInfoAttr? = moveArg.get(AType.REG_DEBUG_INFO)
			for (useArg in ssaVar.useList) {
				val useInsn = useArg.getParentInsn()
				if (useInsn == null) {
					return false
				}
				if (debugInfo == null) {
					val debugInfoAttr = useArg.get(AType.REG_DEBUG_INFO)
					if (debugInfoAttr != null) {
						debugInfo = debugInfoAttr
					}
				}
			}

			// 所有检查通过，执行内联
			for (useArg in ArrayList(ssaVar.useList)) {
				val useInsn = useArg.getParentInsn() ?: continue
				val replaceArg: InsnArg = if (moveArg.isRegister) {
					(moveArg as RegisterArg).duplicate(useArg.getInitType())
				} else {
					moveArg.duplicate()
				}
				useInsn.inheritMetadata(move)
				replaceArg.copyAttributesFrom(useArg)
				if (debugInfo != null) {
					replaceArg.addAttr(debugInfo)
				}
				if (!useInsn.replaceArg(useArg, replaceArg)) {
					mth.addWarnComment("Failed to replace arg in insn: $useInsn")
				}
			}
			return true
		}

		@Suppress("unused")
		private fun deleteMove(mth: MethodNode, move: InsnNode): Boolean {
			val moveArg = move.getArg(0)
			if (!moveArg.isRegister) {
				return false
			}
			val moveReg = moveArg as RegisterArg
			val ssaVar = checkNotNull(moveReg.sVar)
			if (ssaVar.useCount != 1 || ssaVar.isUsedInPhi()) {
				return false
			}
			val assignArg = ssaVar.assign
			val parentInsn = assignArg.getParentInsn() ?: return false
			if (parentInsn.sourceLine != move.sourceLine ||
				moveArg.contains(AType.REG_DEBUG_INFO)
			) {
				// 保留调试信息
				return false
			}
			// 把 move 的结果写回父指令的结果
			InsnRemover.unbindAllArgs(mth, move)
			InsnRemover.unbindResult(mth, parentInsn)

			val resArg = checkNotNull(parentInsn.result)
			val newResArg = checkNotNull(move.result).duplicate(resArg.getInitType())
			newResArg.copyAttributesFrom(resArg)
			parentInsn.setResult(newResArg)
			return true
		}
	}
}
