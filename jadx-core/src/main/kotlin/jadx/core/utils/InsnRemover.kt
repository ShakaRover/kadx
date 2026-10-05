package jadx.core.utils

import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.PhiListAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList

/**
 * 指令删除辅助类，支持在遍历指令列表的过程中安全删除。
 *
 * **核心思路**：先把待删指令收集到 [toRemove]，[perform] 时统一解绑参数/结果再按引用删除，
 * 避免边遍历边删除导致的 `ConcurrentModificationException`。
 *
 * **Kotlin 转换说明**：静态方法用 `companion object` + `@JvmStatic`，Java 调用方零改动。
 */
class InsnRemover {

	private val mth: MethodNode
	private val toRemove: MutableList<InsnNode> = ArrayList()
	private var instrList: MutableList<InsnNode>? = null

	constructor(mth: MethodNode) : this(mth, null)

	constructor(mth: MethodNode, block: BlockNode?) {
		this.mth = mth
		if (block != null) {
			this.instrList = block.instructions
		}
	}

	fun setBlock(block: BlockNode) {
		this.instrList = block.instructions
	}

	fun addAndUnbind(insn: InsnNode) {
		toRemove.add(insn)
		unbindInsn(mth, insn)
	}

	fun addWithoutUnbind(insn: InsnNode) {
		toRemove.add(insn)
	}

	fun perform() {
		if (toRemove.isEmpty()) {
			return
		}
		val list = instrList
		if (list == null) {
			for (remInsn in toRemove) {
				remove(mth, remInsn)
			}
		} else {
			unbindInsns(mth, toRemove)
			removeAll(list, toRemove)
		}
		toRemove.clear()
	}

	fun performForBlock(block: BlockNode) {
		if (toRemove.isEmpty()) {
			return
		}
		instrList = requireNotNull(block.instructions)
		unbindInsns(mth, toRemove)
		removeAll(checkNotNull(instrList), toRemove)
		toRemove.clear()
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(InsnRemover::class.java)
		fun unbindInsn(mth: MethodNode?, insn: InsnNode) {
			unbindAllArgs(mth, insn)
			unbindResult(mth, insn)
			insn.add(AFlag.DONT_GENERATE)
		}

		fun unbindInsns(mth: MethodNode?, insns: List<InsnNode>) {
			// 先移除所有使用，再解绑结果，这样解绑时才能识别未使用的 SSA 变量
			insns.forEach { insn -> unbindAllArgs(mth, insn) }
			insns.forEach { insn ->
				unbindResult(mth, insn)
				insn.add(AFlag.DONT_GENERATE)
			}
		}

		fun unbindAllArgs(mth: MethodNode?, insn: InsnNode) {
			for (arg in insn.getArguments()) {
				unbindArgUsage(mth, arg)
			}
			if (insn.type == InsnType.PHI) {
				for (arg in insn.getArguments()) {
					if (arg is RegisterArg) {
						checkNotNull(arg.sVar).updateUsedInPhiList()
					}
				}
			}
			insn.add(AFlag.REMOVE)
			insn.add(AFlag.DONT_GENERATE)
		}

		fun unbindResult(mth: MethodNode?, insn: InsnNode) {
			val r = insn.result ?: return
			if (mth != null) {
				val ssaVar = r.sVar
				// assignInsn 可能已被重新赋值
				if (ssaVar != null && ssaVar.assignInsn === insn) {
					removeSsaVar(mth, ssaVar)
				}
			}
			insn.setResult(null)
		}

		private fun removeSsaVar(mth: MethodNode, ssaVar: SSAVar) {
			val useCount = ssaVar.useCount
			if (useCount == 0) {
				mth.removeSVar(ssaVar)
				return
			}
			// 检查是否只在 PHI 指令中使用
			if (ListUtils.allMatch(ssaVar.useList) { arg -> InsnUtils.isInsnType(arg.getParentInsn(), InsnType.PHI) }) {
				for (arg in ArrayList(ssaVar.useList)) {
					val parentInsn = arg.getParentInsn()
					if (parentInsn != null) {
						(parentInsn as PhiInsn).removeArg(arg)
					}
				}
				mth.removeSVar(ssaVar)
				return
			}
			// 检查是否只在不会生成的指令中使用
			if (ListUtils.allMatch(ssaVar.useList) { arg ->
					arg.contains(AFlag.DONT_GENERATE) || InsnUtils.contains(arg.getParentInsn(), AFlag.DONT_GENERATE)
				}
			) {
				for (arg in ssaVar.useList) {
					arg.resetSSAVar()
				}
				mth.removeSVar(ssaVar)
				return
			}
			// 仍被使用的变量：不删除，改为把使用点与该 SSA 版本解绑（回退寄存器语义）。
			// 上游在此抛异常会中止强制重处理周期：类停留半处理状态（pass 链不再重跑）、
			// 未跑到 RegionMaker 的方法静默 dump、SSATransform 新建的变量没有 CodeVar，
			// 造成跨类的 "Code variable not set" 连锁失败。后续 pass 会重新推导被解绑
			// 参数的类型。
			LOG.warn(
				"Can't remove SSA var: {} (still in use, count: {}), unbinding uses instead in {}",
				ssaVar, useCount, mth,
			)
			for (arg in ssaVar.useList) {
				arg.resetSSAVar()
			}
			mth.removeSVar(ssaVar)
		}

		fun unbindArgUsage(mth: MethodNode?, arg: InsnArg) {
			if (arg is RegisterArg) {
				val sVar = arg.sVar
				if (sVar != null) {
					sVar.removeUse(arg)
				}
			} else if (arg is InsnWrapArg) {
				unbindInsn(mth, arg.wrapInsn)
			}
		}

		// 不要用 'instrList.removeAll(toRemove)'：那会按内容删除，
		// 而这里可能存在多条内容相同的指令，必须按引用删除
		private fun removeAll(insns: MutableList<InsnNode>, toRemove: List<InsnNode>?) {
			if (toRemove == null || toRemove.isEmpty()) {
				return
			}
			for (rem in toRemove) {
				val insnsCount = insns.size
				var found = false
				for (i in 0 until insnsCount) {
					if (insns[i] === rem) {
						insns.removeAt(i)
						found = true
						break
					}
				}
				if (!found && Consts.DEBUG_WITH_ERRORS) {
					throw JadxRuntimeException(
						"Can't remove insn:" +
							"\n  " + rem +
							"\n not found in list:" +
							"\n  " + Utils.listToString(insns, "\n  "),
					)
				}
			}
		}

		fun remove(mth: MethodNode, insn: InsnNode?) {
			if (insn == null) {
				return
			}
			if (insn.contains(AFlag.WRAPPED)) {
				unbindInsn(mth, insn)
				return
			}
			val block = BlockUtils.getBlockByInsn(mth, insn)
			if (block != null) {
				remove(mth, block, insn)
			} else {
				insn.add(AFlag.DONT_GENERATE)
				mth.addWarnComment("Not found block with instruction: $insn")
			}
		}

		fun remove(mth: MethodNode, block: BlockNode, insn: InsnNode) {
			if (block.contains(AFlag.DUPLICATED)) {
				mth.addWarnComment("Instruction removed from duplicated block: $block, please report this as an issue")
			}
			unbindInsn(mth, insn)
			removeWithoutUnbind(mth, block, insn)
		}

		fun removeWithoutUnbind(mth: MethodNode, block: BlockNode, insn: InsnNode): Boolean {
			// 按指针删除（不要用 equals）
			val it = block.instructions.iterator()
			while (it.hasNext()) {
				val ir = it.next()
				if (ir === insn) {
					it.remove()
					return true
				}
			}
			if (!insn.contains(AFlag.WRAPPED)) {
				mth.addWarnComment("Failed to remove instruction: $insn from block: $block")
			}
			return false
		}

		fun removeAllAndUnbind(mth: MethodNode, block: BlockNode, insns: List<InsnNode>) {
			unbindInsns(mth, insns)
			removeAll(block.instructions, insns)
		}

		fun removeAllAndUnbind(mth: MethodNode, container: IContainer, insns: List<InsnNode>) {
			unbindInsns(mth, insns)
			RegionUtils.visitBlocks(mth, container) { b -> removeAll(asMutable(b.instructions), insns) }
		}

		fun removeAllAndUnbind(mth: MethodNode, insns: List<InsnNode>) {
			unbindInsns(mth, insns)
			val blocks = mth.basicBlocks ?: return
			for (block in blocks) {
				removeAll(block.instructions, insns)
			}
		}

		fun removeAllWithoutUnbind(block: BlockNode, insns: List<InsnNode>) {
			removeAll(block.instructions, insns)
		}

		fun removeAllMarked(mth: MethodNode) {
			val insnRemover = InsnRemover(mth)
			val blocks = mth.basicBlocks ?: return
			for (blockNode in blocks) {
				for (insn in blockNode.instructions) {
					if (insn.contains(AFlag.REMOVE)) {
						insnRemover.addWithoutUnbind(insn)
					}
				}
				insnRemover.setBlock(blockNode)
				insnRemover.perform()
			}
		}

		fun remove(mth: MethodNode, block: BlockNode, index: Int) {
			val instructions = block.instructions
			unbindInsn(mth, instructions[index])
			instructions.removeAt(index)
		}

		fun delistPhi(mth: MethodNode, phiInsn: PhiInsn) {
			val blocks = mth.basicBlocks ?: return
			for (block in blocks) {
				val phiListAttr = block.get(AType.PHI_LIST)
				if (phiListAttr != null) {
					phiListAttr.list.removeIf { i -> i === phiInsn }
				}
			}
		}

		/** 把只读视图转回可变列表（实际对象都是可变列表，仅用于通过编译期检查）。 */
		@Suppress("UNCHECKED_CAST")
		private fun asMutable(list: List<InsnNode>): MutableList<InsnNode> = list as MutableList<InsnNode>
	}
}
