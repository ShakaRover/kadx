package jadx.core.dex.visitors

import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.JumpInfo
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.FillArrayData
import jadx.core.dex.instructions.FillArrayInsn
import jadx.core.dex.instructions.FilledNewArrayNode
import jadx.core.dex.instructions.GotoNode
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.SwitchData
import jadx.core.dex.instructions.SwitchInsn
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.java.JsrNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.blocks.BlockSplitter
import jadx.core.utils.InsnUtils
import jadx.core.utils.exceptions.JadxException
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 初始化指令的附加信息：跳转边、switch/fill-array 的 payload、move-result 合并。
 *
 * **做什么**：遍历按偏移排列的指令数组，为分支/跳转指令登记 [JumpInfo]；
 * 把 `switch`、`fill-array` 的独立 payload 指令挂到主指令上并删除 payload；
 * 把 `invoke`/`filled-new-array`/字符串拼接后面紧跟的 `move-result` 结果合并进主指令。
 *
 * **为什么在块切分之前运行**：块切分（[BlockSplitter]）依赖这些跳转边才能正确建立 CFG。
 */
@JadxVisitor(
	name = "Process Instructions Visitor",
	desc = "Init instructions info",
	runBefore = [
		BlockSplitter::class,
	],
)
class ProcessInstructionsVisitor : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		initJumps(mth, checkNotNull(mth.instructions))
	}

	private fun initJumps(mth: MethodNode, insnByOffset: Array<InsnNode?>) {
		for (offset in insnByOffset.indices) {
			val insn = insnByOffset[offset] ?: continue
			when (insn.type) {
				InsnType.SWITCH -> {
					val sw = insn as SwitchInsn
					if (sw.needData()) {
						attachSwitchData(insnByOffset, offset, sw)
					}
					val defCaseOffset = sw.defaultCaseOffset
					if (defCaseOffset != -1) {
						addJump(mth, insnByOffset, offset, defCaseOffset)
					}
					for (target in sw.getTargets()) {
						addJump(mth, insnByOffset, offset, target)
					}
				}

				InsnType.IF -> {
					val next = getNextInsnOffset(insnByOffset, offset)
					if (next != -1) {
						addJump(mth, insnByOffset, offset, next)
					}
					addJump(mth, insnByOffset, offset, (insn as IfNode).getTarget())
				}

				InsnType.GOTO -> addJump(mth, insnByOffset, offset, (insn as GotoNode).getTarget())

				InsnType.JAVA_JSR -> {
					addJump(mth, insnByOffset, offset, (insn as JsrNode).getTarget())
					val onRet = getNextInsnOffset(insnByOffset, offset)
					if (onRet != -1) {
						addJump(mth, insnByOffset, offset, onRet)
					}
				}

				InsnType.INVOKE -> {
					if (insn.result == null) {
						val retType = (insn as BaseInvokeNode).callMth.returnType
						mergeMoveResult(insnByOffset, offset, insn, retType)
					}
				}

				InsnType.STR_CONCAT -> {
					// invoke-custom 的字符串拼接会直接转成 STR_CONCAT，同样要合并紧随的 move-result
					if (insn.result == null) {
						mergeMoveResult(insnByOffset, offset, insn, ArgType.STRING)
					}
				}

				InsnType.FILLED_NEW_ARRAY -> {
					val arrType = (insn as FilledNewArrayNode).arrayType
					mergeMoveResult(insnByOffset, offset, insn, arrType)
				}

				InsnType.FILL_ARRAY -> {
					val fillArrayInsn = insn as FillArrayInsn
					val target = fillArrayInsn.target
					val arrDataInsn = getInsnAtOffset(insnByOffset, target)
					if (arrDataInsn != null && arrDataInsn.type == InsnType.FILL_ARRAY_DATA) {
						fillArrayInsn.setArrayData(arrDataInsn as FillArrayData)
						removeInsn(insnByOffset, arrDataInsn)
					} else {
						throw JadxRuntimeException("Payload for fill-array not found at " + InsnUtils.formatOffset(target))
					}
				}

				else -> {}
			}
		}
	}

	private fun attachSwitchData(insnByOffset: Array<InsnNode?>, offset: Int, sw: SwitchInsn) {
		val nextInsnOffset = getNextInsnOffset(insnByOffset, offset)
		val dataTarget = sw.dataTarget
		val switchDataInsn = getInsnAtOffset(insnByOffset, dataTarget)
		if (switchDataInsn != null && switchDataInsn.type == InsnType.SWITCH_DATA) {
			val data = switchDataInsn as SwitchData
			data.fixTargets(offset)
			sw.attachSwitchData(data, nextInsnOffset)
			removeInsn(insnByOffset, switchDataInsn)
		} else {
			throw JadxRuntimeException("Payload for switch not found at " + InsnUtils.formatOffset(dataTarget))
		}
	}

	private fun mergeMoveResult(insnByOffset: Array<InsnNode?>, offset: Int, insn: InsnNode, resType: ArgType) {
		val nextInsnOffset = getNextInsnOffset(insnByOffset, offset)
		if (nextInsnOffset == -1) {
			return
		}
		val nextInsn = checkNotNull(insnByOffset[nextInsnOffset])
		if (nextInsn.type != InsnType.MOVE_RESULT) {
			return
		}
		val moveRes = nextInsn.result
		insn.setResult(checkNotNull(moveRes).duplicate(resType))
		insn.copyAttributesFrom(nextInsn)
		removeInsn(insnByOffset, nextInsn)
	}

	private fun addJump(mth: MethodNode, insnByOffset: Array<InsnNode?>, offset: Int, target: Int) {
		try {
			checkNotNull(insnByOffset[target]).addAttr(AType.JUMP, JumpInfo(offset, target))
		} catch (e: Exception) {
			mth.addError("Failed to set jump: " + InsnUtils.formatOffset(offset) + " -> " + InsnUtils.formatOffset(target), e)
		}
	}

	private fun removeInsn(insnByOffset: Array<InsnNode?>, insn: InsnNode) {
		insnByOffset[insn.getOffset()] = null
	}

	companion object {
		fun getNextInsnOffset(insnByOffset: Array<InsnNode?>, offset: Int): Int {
			val len = insnByOffset.size
			for (i in offset + 1 until len) {
				val insnNode = insnByOffset[i]
				if (insnNode != null && insnNode.type != InsnType.NOP) {
					return i
				}
			}
			return -1
		}

		private fun getInsnAtOffset(insnByOffset: Array<InsnNode?>, offset: Int): InsnNode? {
			val len = insnByOffset.size
			for (i in offset until len) {
				val insnNode = insnByOffset[i]
				if (insnNode != null && insnNode.type != InsnType.NOP) {
					return insnNode
				}
			}
			return null
		}
	}
}
