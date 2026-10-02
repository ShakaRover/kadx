package jadx.core.dex.visitors

import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxException
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 代码合法性检查访问者。
 *
 * **做什么**：检查两类明显不合法的情况：
 * 1. 方法参数超过 255 个（Java 规范限制），能安全移除则移除；
 * 2. 指令里引用了越界的寄存器编号（负数或大于方法寄存器数）。
 *
 * **为什么**：越界寄存器属于反编译器的内部一致性错误，必须尽早抛出，避免污染后续 Pass。
 */
@JadxVisitor(
	name = "CheckCode",
	desc = "Check and remove bad or incorrect code",
)
class CheckCode : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		val mthInfo: MethodInfo = mth.getMethodInfo()
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
		if (mth.getUseIn().isEmpty()) {
			return true
		}
		val insns = mth.instructions ?: return true
		if (insns.isEmpty()) {
			return true
		}
		for (insn in insns) {
			if (insn != null && insn.getType() != InsnType.NOP) {
				if (insn.getType() == InsnType.RETURN && insn.getArgsCount() == 0) {
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
			val resultArg: RegisterArg? = insnNode.getResult()
			if (resultArg != null) {
				list.add(resultArg)
			}
			insnNode.getRegisterArgs(list)
			for (arg in list) {
				val regNum = arg.regNum
				if (regNum < 0) {
					throw JadxRuntimeException("Incorrect negative register number in instruction: $insnNode")
				}
				if (regNum >= regsCount) {
					throw JadxRuntimeException(
						"Incorrect register number in instruction: $insnNode, expected to be less than $regsCount",
					)
				}
			}
		}
	}
}
