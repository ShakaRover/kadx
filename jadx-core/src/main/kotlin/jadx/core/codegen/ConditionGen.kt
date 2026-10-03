package jadx.core.codegen

import jadx.api.ICodeWriter
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.ArithNode
import jadx.core.dex.instructions.ArithOp
import jadx.core.dex.instructions.IfOp
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.regions.conditions.Compare
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.dex.regions.conditions.IfCondition.Mode
import jadx.core.utils.exceptions.CodegenException
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayDeque
import java.util.Queue

/**
 * 条件表达式代码生成器：把 [IfCondition] 树渲染成 Java 的 `&&`、`||`、`!`、三元表达式等。
 *
 * 继承 [InsnGen] 以复用参数渲染逻辑（[addArg] 等）。
 *
 * **Kotlin 转换说明**：条件树是 CFG 的图结构，具有身份语义，[CondStack] 仅作临时栈使用。
 */
open class ConditionGen(insnGen: InsnGen) : InsnGen(insnGen.mgen, insnGen.fallback) {

	/** 条件递归遍历用的栈；只保存当前路径上的条件，避免无限递归。 */
	private class CondStack {
		val stack: Queue<IfCondition> = ArrayDeque()

		fun push(cond: IfCondition) {
			stack.add(cond)
		}

		fun pop(): IfCondition? = stack.poll()
	}

	@Throws(CodegenException::class)
	fun add(code: ICodeWriter, condition: IfCondition) {
		add(code, CondStack(), condition)
	}

	@Throws(CodegenException::class)
	internal fun wrap(code: ICodeWriter, condition: IfCondition) {
		wrap(code, CondStack(), condition)
	}

	@Throws(CodegenException::class)
	private fun add(code: ICodeWriter, stack: CondStack, condition: IfCondition) {
		stack.push(condition)
		when (condition.getMode()) {
			Mode.COMPARE -> addCompare(code, stack, checkNotNull(condition.getCompare()))
			Mode.TERNARY -> addTernary(code, stack, condition)
			Mode.NOT -> addNot(code, stack, condition)
			Mode.AND, Mode.OR -> addAndOr(code, stack, condition)
			else -> throw JadxRuntimeException("Unknown condition mode: " + condition.getMode())
		}
		stack.pop()
	}

	@Throws(CodegenException::class)
	private fun wrap(code: ICodeWriter, stack: CondStack, cond: IfCondition) {
		val wrap = isWrapNeeded(cond)
		if (wrap) {
			code.add('(')
		}
		add(code, stack, cond)
		if (wrap) {
			code.add(')')
		}
	}

	@Throws(CodegenException::class)
	private fun wrap(code: ICodeWriter, firstArg: InsnArg) {
		val wrap = isArgWrapNeeded(firstArg)
		if (wrap) {
			code.add('(')
		}
		addArg(code, firstArg, false)
		if (wrap) {
			code.add(')')
		}
	}

	@Throws(CodegenException::class)
	private fun addCompare(code: ICodeWriter, stack: CondStack, compare: Compare) {
		var op = compare.getOp()
		val firstArg = compare.getA()
		val secondArg = compare.getB()
		if (firstArg.getType() == ArgType.BOOLEAN &&
			secondArg.isLiteral &&
			secondArg.getType() == ArgType.BOOLEAN
		) {
			val lit = secondArg as LiteralArg
			if (lit.literal == 0L) {
				op = op.invert()
			}
			if (op == IfOp.EQ) {
				// == true
				if (stack.stack.size == 1) {
					addArg(code, firstArg, false)
				} else {
					wrap(code, firstArg)
				}
				return
			} else if (op == IfOp.NE) {
				// != true
				code.add('!')
				wrap(code, firstArg)
				return
			}
			mth.addWarn("Unsupported boolean condition " + op.symbol)
		}

		addArg(code, firstArg, isArgWrapNeeded(firstArg))
		code.add(' ').add(op.symbol).add(' ')
		addArg(code, secondArg, isArgWrapNeeded(secondArg))
	}

	@Throws(CodegenException::class)
	private fun addTernary(code: ICodeWriter, stack: CondStack, condition: IfCondition) {
		add(code, stack, condition.first())
		code.add(" ? ")
		add(code, stack, condition.second())
		code.add(" : ")
		add(code, stack, condition.third())
	}

	@Throws(CodegenException::class)
	private fun addNot(code: ICodeWriter, stack: CondStack, condition: IfCondition) {
		code.add('!')
		wrap(code, stack, condition.getArgs()[0])
	}

	@Throws(CodegenException::class)
	private fun addAndOr(code: ICodeWriter, stack: CondStack, condition: IfCondition) {
		val mode = if (condition.getMode() == Mode.AND) " && " else " || "
		val it = condition.getArgs().iterator()
		while (it.hasNext()) {
			wrap(code, stack, it.next())
			if (it.hasNext()) {
				code.add(mode)
			}
		}
	}

	private fun isWrapNeeded(condition: IfCondition): Boolean {
		if (condition.isCompare() || condition.contains(AFlag.DONT_WRAP)) {
			return false
		}
		return condition.getMode() != Mode.NOT
	}

	companion object {
		/** 判断某个参数作为子表达式时是否需要额外加括号（根据运算优先级）。 */
		private fun isArgWrapNeeded(arg: InsnArg): Boolean {
			if (!arg.isInsnWrap) {
				return false
			}
			val insn: InsnNode = (arg as InsnWrapArg).wrapInsn
			val insnType = insn.getType()
			if (insnType == InsnType.ARITH) {
				return when ((insn as ArithNode).op) {
					ArithOp.ADD,
					ArithOp.SUB,
					ArithOp.MUL,
					ArithOp.DIV,
					ArithOp.REM,
					-> false

					else -> true
				}
			}
			return when (insnType) {
				InsnType.INVOKE,
				InsnType.SGET,
				InsnType.IGET,
				InsnType.AGET,
				InsnType.CONST,
				InsnType.ARRAY_LENGTH,
				-> false

				else -> true
			}
		}
	}
}
