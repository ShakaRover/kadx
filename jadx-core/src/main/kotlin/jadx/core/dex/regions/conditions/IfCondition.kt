package jadx.core.dex.regions.conditions

import jadx.core.dex.attributes.AttrNode
import jadx.core.dex.instructions.ArithNode
import jadx.core.dex.instructions.ArithOp
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.IfOp
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.BlockUtils
import java.util.Arrays
import java.util.Collections

/**
 * 结构化条件表达式树。
 *
 * **五种模式**（[Mode]）：
 * - COMPARE：叶子节点，包装一条 if 指令（[Compare]）；
 * - TERNARY：三目 `a ? b : c`；
 * - NOT：逻辑非；
 * - AND / OR：逻辑与 / 或（[args] 中保存子条件）。
 *
 * 本类会做大量“规范化/简化”工作（[simplify]），把编译器生成的复杂跳转还原成
 * 人类可读的布尔表达式。它属于表达式树节点，**不是值对象**，但原 Java 特意实现了
 * [equals]/[hashCode] 用于条件去重，这里原样保留。
 *
 * Kotlin 转换说明：
 * - 私有构造器 + 静态工厂（companion + `@JvmStatic`），Java 调用方写法不变；
 * - [args] 需要被 [addArg] 原地修改，故用 MutableList；COMPARE 分支的空列表沿用
 *   不可变列表语义；
 * - 原 Java 的 `updated != condition` 等引用比较一律写成 `!==`。
 */
class IfCondition private constructor(
	private val mode: Mode,
	private val args: MutableList<IfCondition>,
	private val compare: Compare?,
) : AttrNode() {

	enum class Mode {
		COMPARE,
		TERNARY,
		NOT,
		AND,
		OR,
	}

	/** COMPARE 叶子：只有一个比较，没有子条件 */
	private constructor(compare: Compare) : this(Mode.COMPARE, Collections.emptyList(), compare)

	/** 逻辑节点（TERNARY / NOT / AND / OR）：没有 Compare */
	private constructor(mode: Mode, args: MutableList<IfCondition>) : this(mode, args, null)

	/** 拷贝构造：非 COMPARE 模式下复制一份可变的子条件列表，避免共享被误改 */
	private constructor(c: IfCondition) : this(
		c.mode,
		if (c.mode == Mode.COMPARE) Collections.emptyList() else ArrayList(c.args),
		c.compare,
	)

	fun getMode(): Mode = mode

	fun getArgs(): MutableList<IfCondition> = args

	fun first(): IfCondition = args[0]

	fun second(): IfCondition = args[1]

	fun third(): IfCondition = args[2]

	fun addArg(c: IfCondition) {
		args.add(c)
	}

	fun isCompare(): Boolean = mode == Mode.COMPARE

	fun getCompare(): Compare? = compare

	fun getRegisterArgs(): List<RegisterArg> {
		val list = ArrayList<RegisterArg>()
		if (mode == Mode.COMPARE) {
			checkNotNull(compare).getInsn().getRegisterArgs(list)
		} else {
			for (arg in args) {
				list.addAll(arg.getRegisterArgs())
			}
		}
		return list
	}

	fun replaceArg(from: InsnArg, to: InsnArg): Boolean {
		if (mode == Mode.COMPARE) {
			return checkNotNull(compare).getInsn().replaceArg(from, to)
		}
		for (arg in args) {
			if (arg.replaceArg(from, to)) {
				return true
			}
		}
		return false
	}

	fun visitInsns(visitor: (InsnNode) -> Unit) {
		if (mode == Mode.COMPARE) {
			checkNotNull(compare).getInsn().visitInsns(visitor)
		} else {
			for (arg in args) {
				arg.visitInsns(visitor)
			}
		}
	}

	fun collectInsns(): List<InsnNode> {
		val list = ArrayList<InsnNode>()
		visitInsns { list.add(it) }
		return list
	}

	fun getSourceLine(): Int {
		for (insn in collectInsns()) {
			val line = insn.getSourceLine()
			if (line != 0) {
				return line
			}
		}
		return 0
	}

	fun getFirstInsn(): InsnNode? {
		if (mode == Mode.COMPARE) {
			return checkNotNull(compare).getInsn()
		}
		return args[0].getFirstInsn()
	}

	override fun toString(): String = when (mode) {
		Mode.COMPARE -> checkNotNull(compare).toString()

		Mode.TERNARY -> first().toString() + " ? " + second() + " : " + third()

		Mode.NOT -> "!(" + first() + ')'

		Mode.AND, Mode.OR -> {
			val op = if (mode == Mode.OR) " || " else " && "
			val sb = StringBuilder()
			sb.append('(')
			val it = args.iterator()
			while (it.hasNext()) {
				sb.append(it.next())
				if (it.hasNext()) {
					sb.append(op)
				}
			}
			sb.append(')')
			sb.toString()
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is IfCondition) {
			return false
		}
		if (mode != other.mode) {
			return false
		}
		return args == other.args && compare == other.compare
	}

	override fun hashCode(): Int {
		var result = super.hashCode()
		result = 31 * result + mode.hashCode()
		result = 31 * result + args.hashCode()
		result = 31 * result + (compare?.hashCode() ?: 0)
		return result
	}

	companion object {
		/**
		 * 从块的最后一条指令（应为 if 指令）解析条件。
		 *
		 * 若块为空则返回 null（原 Java 也允许返回 null）。
		 */
		@JvmStatic
		fun fromIfBlock(header: BlockNode): IfCondition? {
			val lastInsn = BlockUtils.getLastInsn(header)
			if (lastInsn == null) {
				return null
			}
			return fromIfNode(lastInsn as IfNode)
		}

		@JvmStatic
		fun fromIfNode(insn: IfNode): IfCondition = IfCondition(Compare(insn))

		@JvmStatic
		fun ternary(a: IfCondition, b: IfCondition, c: IfCondition): IfCondition = IfCondition(Mode.TERNARY, Arrays.asList(a, b, c))

		/**
		 * 用逻辑运算符 [mode] 合并两个条件。
		 *
		 * 若 [a] 已经是同种运算（如 AND），则把 [b] 追加到 [a] 的副本上，
		 * 避免出现 `(a AND b) AND c` 这种嵌套。
		 */
		@JvmStatic
		fun merge(mode: Mode, a: IfCondition, b: IfCondition): IfCondition {
			if (a.getMode() == mode) {
				val n = IfCondition(a)
				n.addArg(b)
				return n
			}
			return IfCondition(mode, Arrays.asList(a, b))
		}

		/** 递归对整个条件树取反（德摩根律：AND/OR 互换，子条件各自取反） */
		@JvmStatic
		fun invert(cond: IfCondition): IfCondition {
			val mode = cond.getMode()
			return when (mode) {
				Mode.COMPARE -> IfCondition(checkNotNull(cond.getCompare()).invert())

				Mode.TERNARY -> ternary(cond.first(), not(cond.second()), not(cond.third()))

				Mode.NOT -> cond.first()

				Mode.AND, Mode.OR -> {
					val args = cond.getArgs()
					val newArgs = ArrayList<IfCondition>(args.size)
					for (arg in args) {
						newArgs.add(invert(arg))
					}
					IfCondition(if (mode == Mode.AND) Mode.OR else Mode.AND, newArgs)
				}
			}
		}

		/** 逻辑非：双重否定消去；COMPARE 直接取反比较；其余包一层 NOT */
		@JvmStatic
		fun not(cond: IfCondition): IfCondition {
			if (cond.getMode() == Mode.NOT) {
				return cond.first()
			}
			val compare = cond.getCompare()
			if (compare != null) {
				return IfCondition(compare.invert())
			}
			return IfCondition(Mode.NOT, Collections.singletonList(cond))
		}

		/**
		 * 条件化简主入口：
		 * 1. 对 COMPARE 叶子尝试把 `(a cmp b) cmp 0/1` 折叠成更简单的比较；
		 * 2. 把 `x == false` 改写为 `!x`；
		 * 3. 递归化简子条件；
		 * 4. 消除双重否定，并在否定过多时整体取反。
		 */
		@JvmStatic
		fun simplify(cond: IfCondition): IfCondition {
			var current = cond
			if (current.isCompare()) {
				val c = checkNotNull(current.getCompare())
				val i = simplifyCmpOp(c)
				if (i != null) {
					return i
				}
				if (c.getOp() == IfOp.EQ && c.getB().isFalse()) {
					current = IfCondition(Mode.NOT, Collections.singletonList(IfCondition(c.invert())))
				} else {
					c.normalize()
				}
			}
			var newArgs: MutableList<IfCondition>? = null
			val currentArgs = current.getArgs()
			for (i in currentArgs.indices) {
				val arg = currentArgs[i]
				val simpl = simplify(arg)
				if (simpl !== arg) {
					var args = newArgs
					if (args == null) {
						args = ArrayList(currentArgs)
						newArgs = args
					}
					args[i] = simpl
				}
			}
			val replacedArgs = newArgs
			if (replacedArgs != null) {
				// 子条件发生了变化，重建当前节点
				current = IfCondition(current.getMode(), replacedArgs)
			}
			if (current.getMode() == Mode.NOT && current.first().getMode() == Mode.NOT) {
				current = invert(current.first())
			}
			if (current.getMode() == Mode.TERNARY && current.first().getMode() == Mode.NOT) {
				current = invert(current)
			}

			// 对包含大量否定的 AND/OR 条件整体取反，输出更自然
			if (current.getMode() == Mode.OR || current.getMode() == Mode.AND) {
				val count = current.getArgs().size
				if (count > 1) {
					var negCount = 0
					for (arg in current.getArgs()) {
						if (arg.getMode() == Mode.NOT ||
							(arg.isCompare() && checkNotNull(arg.getCompare()).getOp() == IfOp.NE)
						) {
							negCount++
						}
					}
					if (negCount > count / 2) {
						return not(invert(current))
					}
				}
			}
			return current
		}

		/**
		 * 尝试把 `wrap(a, b) cmp 0/1` 折叠成更直接的条件。
		 *
		 * 两种情况：
		 * - `CMP_L/CMP_G`（比较指令）：`(a <=> b) == 0` 直接变成 `a op b`；
		 * - `ARITH` 的布尔 AND/OR：`(a | b) != 0` 展开成 `a != false || b != false`。
		 *
		 * 若无法化简返回 null。
		 */
		private fun simplifyCmpOp(c: Compare): IfCondition? {
			if (!c.getA().isInsnWrap) {
				return null
			}
			if (!c.getB().isLiteral) {
				return null
			}
			val lit = (c.getB() as LiteralArg).literal
			if (lit != 0L && lit != 1L) {
				return null
			}

			val wrapInsn = (c.getA() as InsnWrapArg).wrapInsn
			when (wrapInsn.getType()) {
				InsnType.CMP_L, InsnType.CMP_G -> {
					if (lit == 0L) {
						val insn = c.getInsn()
						insn.changeCondition(insn.getOp(), wrapInsn.getArg(0), wrapInsn.getArg(1))
					}
				}

				InsnType.ARITH -> {
					if (c.getB().getType() == ArgType.BOOLEAN) {
						val arithOp = (wrapInsn as ArithNode).op
						if (arithOp == ArithOp.OR || arithOp == ArithOp.AND) {
							val ifOp = c.getInsn().getOp()
							val isTrue = (ifOp == IfOp.NE && lit == 0L) || (ifOp == IfOp.EQ && lit == 1L)

							val op = if (isTrue) IfOp.NE else IfOp.EQ
							val mode = if ((isTrue && arithOp == ArithOp.OR) || (!isTrue && arithOp == ArithOp.AND)) {
								Mode.OR
							} else {
								Mode.AND
							}

							val if1 = IfNode(op, -1, wrapInsn.getArg(0), LiteralArg.litFalse())
							val if2 = IfNode(op, -1, wrapInsn.getArg(1), LiteralArg.litFalse())
							return IfCondition(
								mode,
								Arrays.asList(IfCondition(Compare(if1)), IfCondition(Compare(if2))),
							)
						}
					}
				}

				else -> {}
			}

			return null
		}
	}
}
