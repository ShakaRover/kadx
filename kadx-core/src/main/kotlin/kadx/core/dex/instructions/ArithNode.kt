package kadx.core.dex.instructions

import kadx.api.plugins.input.insns.InsnData
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.InsnUtils
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * 算术 / 位运算指令（add、sub、and、shl 等）。
 *
 * 位运算（AND / OR / XOR）的结果类型会被修正为布尔或窄整型，参数类型也会收窄，
 * 这是因为它们常用于布尔表达式；见 [build] / [buildLit] 中的修正逻辑。
 *
 * Kotlin 转换说明：
 * - [op] 声明为属性，JVM getter 名就是 `getOp()`；
 * - 静态工厂方法放入 companion + `@JvmStatic`，Java 调用方仍写 `ArithNode.build(...)`。
 */
open class ArithNode(val op: ArithOp, res: RegisterArg?, a: InsnArg, b: InsnArg) : InsnNode(InsnType.ARITH, 2) {

	init {
		setResult(res)
		addArg(a)
		addArg(b)
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is ArithNode || !super.isSame(obj)) {
			return false
		}
		return op == obj.op && isSameLiteral(obj)
	}

	private fun isSameLiteral(other: ArithNode): Boolean {
		val thisSecond = getArg(1)
		val otherSecond = other.getArg(1)
		if (thisSecond.isLiteral != otherSecond.isLiteral) {
			return false
		}
		if (!thisSecond.isLiteral) {
			// 两个参数都不是字面量，无需比较数值
			return true
		}
		// 两个参数都是字面量，比较数值是否相同
		val thisLit = (thisSecond as LiteralArg).literal
		val otherLit = (otherSecond as LiteralArg).literal
		return thisLit == otherLit
	}

	override fun copy(): InsnNode {
		val copy = ArithNode(op, null, getArg(0).duplicate(), getArg(1).duplicate())
		return copyCommonParams(copy)
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(InsnUtils.formatOffset(offset))
		sb.append(": ARITH ")
		if (contains(AFlag.ARITH_ONEARG)) {
			// 单参数形式：a += 2
			sb.append(getArg(0)).append(' ').append(op.symbol).append("= ").append(getArg(1))
		} else {
			val result = result
			if (result != null) {
				sb.append(result).append(" = ")
			}
			sb.append(getArg(0)).append(' ').append(op.symbol).append(' ').append(getArg(1))
		}
		appendAttributes(sb)
		return sb.toString()
	}

	companion object {
		fun build(insn: InsnData, op: ArithOp, type: ArgType): ArithNode {
			val resArg = InsnArg.reg(insn, 0, fixResultType(op, type))
			val argType = fixArgType(op, type)
			return when (insn.regsCount) {
				2 -> ArithNode(op, resArg, InsnArg.reg(insn, 0, argType), InsnArg.reg(insn, 1, argType))
				3 -> ArithNode(op, resArg, InsnArg.reg(insn, 1, argType), InsnArg.reg(insn, 2, argType))
				else -> throw KadxRuntimeException("Unexpected registers count in $insn")
			}
		}

		fun buildLit(insn: InsnData, op: ArithOp, type: ArgType): ArithNode {
			val resArg = InsnArg.reg(insn, 0, fixResultType(op, type))
			val argType = fixArgType(op, type)
			val litArg = InsnArg.lit(insn, argType)
			return when (insn.regsCount) {
				1 -> ArithNode(op, resArg, InsnArg.reg(insn, 0, argType), litArg)
				2 -> ArithNode(op, resArg, InsnArg.reg(insn, 1, argType), litArg)
				else -> throw KadxRuntimeException("Unexpected registers count in $insn")
			}
		}

		/**
		 * 创建单参数算术指令（如 `a += 2`），此时结果不单独设置（为 null）。
		 *
		 * @param res 被修改的参数
		 */
		fun oneArgOp(op: ArithOp, res: InsnArg, a: InsnArg): ArithNode {
			val insn = ArithNode(op, null, res, a)
			insn.add(AFlag.ARITH_ONEARG)
			return insn
		}

		private fun fixResultType(op: ArithOp, type: ArgType): ArgType {
			if (type === ArgType.INT && op.isBitOp()) {
				return ArgType.INT_BOOLEAN
			}
			return type
		}

		private fun fixArgType(op: ArithOp, type: ArgType): ArgType {
			if (type === ArgType.INT && op.isBitOp()) {
				return ArgType.NARROW_NUMBERS_NO_FLOAT
			}
			return type
		}
	}
}
