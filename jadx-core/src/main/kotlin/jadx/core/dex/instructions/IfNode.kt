package jadx.core.dex.instructions

import jadx.api.plugins.input.insns.InsnData
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.BlockUtils.getBlockByOffset
import jadx.core.utils.BlockUtils.selectOther
import jadx.core.utils.InsnUtils

/**
 * 条件跳转指令（if-eq / if-lt 等）。
 *
 * 它继承 [GotoNode]（因为条件成立时会跳转），并额外记录“成立分支” [thenBlock]
 * 与“不成立分支” [elseBlock]。注意 [getTarget] 被覆写：块切分完成后返回
 * then 分支首指令的偏移，而不是原始目标偏移。
 *
 * Kotlin 转换说明：
 * - `op` 是可变 protected 字段，Java 侧没有直接字段访问，但为兼容潜在子类
 *   仍用 `@JvmField` 暴露为字段，避免与 [getOp] 的 getter 冲突；
 * - [getTarget] 保持 `open` 以便被覆写。
 */
open class IfNode : GotoNode {

	@JvmField
	protected var op: IfOp

	private var thenBlock: BlockNode? = null
	private var elseBlock: BlockNode? = null

	constructor(insn: InsnData, op: IfOp) : super(InsnType.IF, insn.getTarget(), 2) {
		this.op = op
		val argType = narrowTypeByOp(op)
		addArg(InsnArg.reg(insn, 0, argType))
		if (insn.getRegsCount() == 1) {
			addArg(InsnArg.lit(0, argType))
		} else {
			addArg(InsnArg.reg(insn, 1, argType))
		}
	}

	constructor(op: IfOp, targetOffset: Int, arg1: InsnArg, arg2: InsnArg) : this(op, targetOffset) {
		addArg(arg1)
		addArg(arg2)
	}

	private constructor(op: IfOp, targetOffset: Int) : super(InsnType.IF, targetOffset, 2) {
		this.op = op
	}

	fun getOp(): IfOp = op

	/** 条件取反，同时交换 then / else 两个分支块。 */
	fun invertCondition() {
		op = op.invert()
		val tmp = thenBlock
		thenBlock = elseBlock
		elseBlock = tmp
	}

	/** 把 `a != false` 规范化为 `a == true`。 */
	fun normalize() {
		if (getOp() == IfOp.NE && getArg(1).isFalse()) {
			changeCondition(IfOp.EQ, getArg(0), LiteralArg.litTrue())
		}
	}

	fun changeCondition(op: IfOp, arg1: InsnArg, arg2: InsnArg) {
		this.op = op
		setArg(0, arg1)
		setArg(1, arg2)
	}

	override fun initBlocks(curBlock: BlockNode) {
		val successors = curBlock.getSuccessors()
		thenBlock = getBlockByOffset(target, successors)
		if (successors.size == 1) {
			elseBlock = thenBlock
		} else {
			elseBlock = selectOther(thenBlock, successors)
		}
	}

	override fun replaceTargetBlock(origin: BlockNode, replace: BlockNode): Boolean {
		var replaced = false
		if (thenBlock === origin) {
			thenBlock = replace
			replaced = true
		}
		if (elseBlock === origin) {
			elseBlock = replace
			replaced = true
		}
		return replaced
	}

	fun getThenBlock(): BlockNode? = thenBlock

	fun getElseBlock(): BlockNode? = elseBlock

	override fun getTarget(): Int = thenBlock?.startOffset ?: target

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is IfNode || !super.isSame(obj)) {
			return false
		}
		return op == obj.op
	}

	override fun copy(): InsnNode {
		val copy = IfNode(op, target)
		copy.thenBlock = thenBlock
		copy.elseBlock = elseBlock
		return copyCommonParams(copy)
	}

	override fun toString(): String = InsnUtils.formatOffset(offset) + ": " +
		InsnUtils.insnTypeToString(insnType) +
		getArg(0) + " " + op.symbol + " " + getArg(1) +
		"  -> " + (thenBlock ?: InsnUtils.formatOffset(target)) +
		attributesString()

	companion object {
		// 改变默认类型优先级：EQ / NE 可以比较更宽的类型
		private val WIDE_TYPE = ArgType.unknown(
			PrimitiveType.INT,
			PrimitiveType.BOOLEAN,
			PrimitiveType.OBJECT,
			PrimitiveType.ARRAY,
			PrimitiveType.BYTE,
			PrimitiveType.SHORT,
			PrimitiveType.CHAR,
		)

		private val NUMBERS_TYPE = ArgType.unknown(
			PrimitiveType.INT,
			PrimitiveType.BYTE,
			PrimitiveType.SHORT,
			PrimitiveType.CHAR,
		)

		private fun narrowTypeByOp(op: IfOp): ArgType {
			if (op == IfOp.EQ || op == IfOp.NE) {
				return WIDE_TYPE
			}
			return NUMBERS_TYPE
		}
	}
}
