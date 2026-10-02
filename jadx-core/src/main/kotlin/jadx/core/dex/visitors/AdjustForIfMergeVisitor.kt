package jadx.core.dex.visitors

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.SpecialEdgeAttr
import jadx.core.dex.attributes.nodes.SpecialEdgeAttr.SpecialEdgeType
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.regions.RegionMakerVisitor
import jadx.core.dex.visitors.typeinference.FinishTypeInference
import jadx.core.utils.BlockUtils
import java.util.Collections

/**
 * 调整 if 合并的访问者。
 *
 * **做什么**：寻找“两个简单 if 之间夹着一个过渡块”的形状，尝试把该过渡块中
 * 不影响语义的指令（如对同一寄存器的自赋值 move）下推到 if 的分支里，
 * 从而让两个 if 有机会合并成更简洁的代码。
 *
 * **为什么**：DEX 编译器常生成这种中间块，阻碍后续 if 合并优化。
 *
 * **安全约束**：如果 blk→succ 的边是回边（back edge），下推会破坏循环语义，故拒绝。
 */
@JadxVisitor(
	name = "AdjustForIfMergeVisitor",
	desc = "Move instructions between if blocks that can't be inlined but are safe to push through the if to allow the ifs to merge",
	runBefore = [RegionMakerVisitor::class],
	runAfter = [FinishTypeInference::class],
)
class AdjustForIfMergeVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		// 选出位于两条 if 语句之间的块作为候选
		val blocks = mth.getBasicBlocks() ?: return

		for (blk in blocks) {
			if (areSurroundingsCorrectShape(blk)) {
				val pred = blk.getPredecessors()[0]
				val succ = checkNotNull(blk.getCleanSuccessors())[0]

				if (isSimpleIf(pred) && isSimpleIf(succ)) {
					val movableInstructions = getMovableInstructions(blk, succ)

					if (movableInstructions.isNotEmpty() && couldMerge(mth, pred, blk, succ)) {
						doMove(blk, succ, movableInstructions)
					}
				}
			}
		}
	}

	private fun areSurroundingsCorrectShape(blk: BlockNode): Boolean = blk.getPredecessors().size == 1 && checkNotNull(blk.getCleanSuccessors()).size == 1

	private fun isSimpleIf(blk: BlockNode): Boolean = blk.getInstructions().size == 1 && blk.getInstructions()[0].getType() == InsnType.IF

	private fun couldMerge(mth: MethodNode, pred: BlockNode, blk: BlockNode, succ: BlockNode): Boolean {
		// blk→succ 若是回边则不能合并。
		// BlockUtils 里有个判断回边的函数，但实际不准确，这里手动判断。
		val specialEdges = mth.getAll(AType.SPECIAL_EDGE)
		for (edge in specialEdges) {
			if (edge.start === blk && edge.end === succ && edge.type == SpecialEdgeType.BACK_EDGE) {
				mth.addDebugComment("Refusing to push insns through at block $blk : edge to successor is a back edge.")
				return false
			}
		}
		return true
	}

	private fun getMovableInstructions(blk: BlockNode, succ: BlockNode): MutableList<InsnNode> {
		// “可移动指令”指既不影响代码生成、也不影响后续块语义的指令。
		// 目前只处理“同一寄存器的 nop move”，且目标变量不在 succ 块中被使用。
		val movableInstructions = ArrayList<InsnNode>()
		for (insn in blk.getInstructions()) {
			if (insn.getType() == InsnType.MOVE) {
				val arg0 = insn.getArg(0)
				if (arg0 !is RegisterArg) {
					// 可能是 LiteralArg
					continue
				}
				val source = arg0
				val target = checkNotNull(insn.getResult())

				val uses = checkNotNull(target.sVar).getUseList()
				for (use in uses) {
					val parentInsn = use.getParentInsn()
					if (parentInsn != null && BlockUtils.blockContains(succ, parentInsn)) {
						// 目标在后续块中被使用，不能干净地在其之后做赋值
						continue
					}
				}

				// 不想把所有指令都下推，例如：
				// if (condition) { return; }
				// x = 123456
				// if (condition) { return; }
				// 若把赋值推进第二个 if 的块里，结果反而更差。
				if (source.regNum == target.regNum) {
					movableInstructions.add(insn)
				}
			}
		}
		return movableInstructions
	}

	private fun doMove(target: BlockNode, bottomIf: BlockNode, movableInstructions: MutableList<InsnNode>) {
		// 把指令从 blk 移出，放进 succ 每条出边上的新合成块
		// 保持原指令顺序（虽然这里通常无关紧要）
		Collections.reverse(movableInstructions)
		for (insn in movableInstructions) {
			target.instructions.remove(insn)
			for (succ in checkNotNull(bottomIf.getCleanSuccessors())) {
				succ.instructions.add(0, insn) // 插到开头

				if (succ.contains(AFlag.LOOP_START)) {
					// 若合并进循环条件，抑制“循环头有多条指令”的告警
					succ.add(AFlag.ALLOW_MULTIPLE_INSNS_LOOP_COND)
				}
			}
		}
	}
}
