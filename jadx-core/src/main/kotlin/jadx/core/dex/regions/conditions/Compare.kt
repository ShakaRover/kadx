package jadx.core.dex.regions.conditions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.IfOp
import jadx.core.dex.instructions.args.InsnArg

/**
 * 对一条条件跳转指令 [IfNode] 的薄封装，供 [IfCondition] 的 COMPARE 模式使用。
 *
 * **为什么要包一层？** 条件表达式最终都要落到具体的 `if` 指令上；构造 [Compare]
 * 时会给该指令打上 [AFlag.HIDDEN] 标记，表示它不再作为独立语句生成，而是被提升
 * 成了条件表达式的一部分。
 *
 * 这是指令包装（身份语义），保持普通 class。
 */
class Compare(private val insn: IfNode) {

	init {
		insn.add(AFlag.HIDDEN)
	}

	fun getOp(): IfOp = insn.getOp()

	fun getA(): InsnArg = insn.getArg(0)

	fun getB(): InsnArg = insn.getArg(1)

	fun getInsn(): IfNode = insn

	/** 条件取反，直接修改底层指令并返回自身 */
	fun invert(): Compare {
		insn.invertCondition()
		return this
	}

	fun normalize() {
		insn.normalize()
	}

	override fun toString(): String = getA().toString() + " " + getOp().symbol + ' ' + getB()
}
