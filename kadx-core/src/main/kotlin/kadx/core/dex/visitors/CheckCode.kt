package kadx.core.dex.visitors

import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * 代码合法性检查访问者。
 *
 * **做什么**：检查两类明显不合法的情况：
 * 1. 方法参数超过 255 个（Java 规范限制），能安全移除则移除；
 * 2. 指令里引用了越界的寄存器编号（负数或大于方法寄存器数）。
 *
 * **为什么**：越界寄存器属于反编译器的内部一致性错误，必须尽早抛出，避免污染后续 Pass。
 */
@KadxVisitor(
	name = "CheckCode",
	desc = "Check and remove bad or incorrect code",
)
class CheckCode : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		val mthInfo: MethodInfo = mth.methodInfo
		if (mthInfo.argumentsTypes.size > 255) {
			// Java 规范不允许超过 255 个参数
			if (canRemoveMethod(mth)) {
				mth.ignoreMethod()
			} else {
				// TODO: 把参数转成数组
			}
		}
		checkInstructions(mth)
	}

	private fun canRemoveMethod(mth: MethodNode): Boolean {
		if (mth.useIn.isEmpty()) {
			return true
		}
		val insns = mth.instructions ?: return true
		if (insns.isEmpty()) {
			return true
		}
		for (insn in insns) {
			if (insn != null && insn.type != InsnType.NOP) {
				if (insn.type == InsnType.RETURN && insn.argsCount == 0) {
					// 忽略 void 返回
				} else {
					// 发现有用指令
					return false
				}
			}
		}
		return true
	}

	fun checkInstructions(mth: MethodNode) {
		val insns = mth.instructions
		if (Utils.isEmpty(insns)) {
			return
		}
		val regsCount = mth.getRegsCount()
		val list = ArrayList<RegisterArg>()
		for (insnNode in checkNotNull(insns)) {
			if (insnNode == null) {
				continue
			}
			list.clear()
			val resultArg: RegisterArg? = insnNode.result
			if (resultArg != null) {
				list.add(resultArg)
			}
			insnNode.getRegisterArgs(list)
			for (arg in list) {
				val regNum = arg.regNum
				if (regNum < 0) {
					throw KadxRuntimeException("Incorrect negative register number in instruction: $insnNode")
				}
				if (regNum >= regsCount) {
					throw KadxRuntimeException(
						"Incorrect register number in instruction: $insnNode, expected to be less than $regsCount",
					)
				}
			}
		}
	}
}
