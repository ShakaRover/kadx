package jadx.core.dex.visitors.finaly

import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.RegDebugInfoAttr
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.InsnNode
import java.util.Objects

/**
 * [SameInstructionsStrategy] 的默认实现：按“指令 + 参数”逐项比较，判断重复的 finally 指令。
 *
 * **判定规则**：
 * - 指令本身用 [InsnNode.isSame] 比较；
 * - 寄存器参数：SSA 变量相同、或调试信息相同、或赋值指令等价，三者满足其一即视为相同；
 * - 常量参数：必须值相等。
 *
 * **Kotlin 转换说明**：两个私有静态辅助方法放入 `companion object`（无需 `@JvmStatic`，
 * 因为只有本类内部调用）。原 Java 的 `==` 对象比较在此类中都是对平台对象的 equals 调用，
 * 保持不变。
 */
class SameInstructionsStrategyImpl : SameInstructionsStrategy() {

	override fun sameInsns(dupInsn: InsnNode, fInsn: InsnNode): Boolean {
		if (!dupInsn.isSame(fInsn)) {
			return false
		}
		for (i in 0 until dupInsn.argsCount) {
			val dupArg = dupInsn.getArg(i)
			val fArg = fInsn.getArg(i)
			if (!isSameArgs(dupArg, fArg)) {
				return false
			}
		}
		return true
	}

	override fun isSameArgs(dupArg: InsnArg?, fArg: InsnArg): Boolean {
		if (dupArg == null) {
			return false
		}
		val isReg = dupArg.isRegister
		if (isReg != fArg.isRegister) {
			return false
		}
		if (isReg) {
			val dupReg = dupArg as RegisterArg
			val fReg = fArg as RegisterArg
			if (!dupReg.sameCodeVar(fReg) &&
				!sameDebugInfo(dupReg, fReg) &&
				assignInsnDifferent(dupReg, fReg)
			) {
				return false
			}
		}
		val remConst = dupArg.isConst()
		if (remConst != fArg.isConst()) {
			return false
		}
		return !(remConst && !dupArg.isSameConst(fArg))
	}

	companion object {
		/** 两个寄存器参数是否携带相同的调试信息（局部变量名/类型）。 */
		private fun sameDebugInfo(dupReg: RegisterArg, fReg: RegisterArg): Boolean {
			val fDbgInfo = fReg.get(AType.REG_DEBUG_INFO)
			val dupDbgInfo = dupReg.get(AType.REG_DEBUG_INFO)
			if (fDbgInfo == null || dupDbgInfo == null) {
				return false
			}
			return dupDbgInfo == fDbgInfo
		}

		/**
		 * 两个寄存器参数的“赋值指令”是否不同。
		 *
		 * 原实现中常量指令那一支比较的是同一个列表（历史遗留），此处按原样保留，
		 * 保证行为完全一致。
		 */
		private fun assignInsnDifferent(dupReg: RegisterArg, fReg: RegisterArg): Boolean {
			val assignInsn = fReg.assignInsn
			val dupAssign = dupReg.assignInsn
			if (assignInsn == null || dupAssign == null) {
				return true
			}
			if (!assignInsn.isSame(dupAssign)) {
				return true
			}
			if (assignInsn.isConstInsn && dupAssign.isConstInsn) {
				// 这里不比较常量值本身，只比较参数列表（原 Java 即如此，保留原始行为）
				return !Objects.equals(assignInsn.getArguments(), assignInsn.getArguments())
			}
			return false
		}
	}
}
