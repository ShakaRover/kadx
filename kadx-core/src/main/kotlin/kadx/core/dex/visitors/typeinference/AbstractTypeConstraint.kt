package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.Utils
import java.util.ArrayList

/**
 * [ITypeConstraint] 的抽象基类：负责收集“相关变量”。
 *
 * **算法意图**：多变量搜索（[TypeSearch]）需要知道一条指令上哪些 SSA 变量
 * 会互相影响。本类根据约束关注的是指令结果还是某个参数，收集同指令的其它寄存器变量：
 * - 若 [arg] 就是指令结果，则收集所有寄存器参数对应的变量；
 * - 否则先加入结果变量，再加入除 [arg] 外的其它寄存器参数变量。
 *
 * **Kotlin 转换说明**：保持 `abstract` 且构造器签名不变，
 * 以便 Java 侧仍能写 `new AbstractTypeConstraint(insn, arg) { ... }` 匿名子类。
 */
abstract class AbstractTypeConstraint(
	private val insn: InsnNode,
	arg: InsnArg,
) : ITypeConstraint {

	override val relatedVars: List<SSAVar> = collectRelatedVars(insn, arg)

	private fun collectRelatedVars(insn: InsnNode, arg: InsnArg): List<SSAVar> {
		val list = ArrayList<SSAVar>(insn.argsCount)
		if (insn.result === arg) {
			// 约束针对结果：所有寄存器参数都会影响结果的类型
			for (insnArg in insn.getArguments()) {
				if (insnArg.isRegister) {
					list.add(checkNotNull((insnArg as RegisterArg).sVar))
				}
			}
		} else {
			// 约束针对某个参数：结果与其余寄存器参数都会与之相互影响
			list.add(checkNotNull(insn.result?.sVar))
			for (insnArg in insn.getArguments()) {
				if (insnArg !== arg && insnArg.isRegister) {
					list.add(checkNotNull((insnArg as RegisterArg).sVar))
				}
			}
		}
		return list
	}

	override fun toString(): String = "(" + insn.type + ':' + Utils.listToString(relatedVars) { it.toShortString() } + ')'
}
