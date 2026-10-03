package jadx.core.dex.visitors.shrink

import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.EmptyBitSet
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.BitSet

/**
 * 单条指令的“收缩（shrink）分析”上下文。
 *
 * **做什么**：为一个基本块里的每条指令建立 [ArgsInfo]，记录它的寄存器参数、
 * 可内联边界（[inlineBorder]）、被内联进它的指令（[wrappedInsns]）等，
 * 从而判断某条赋值指令能否安全地内联到使用它的位置。
 *
 * **为什么要区分 assign 与 use**：内联本质是把“先算后存”的指令搬到使用处，
 * 必须保证搬运区间内没有其它指令读/写相同寄存器（[canMove]），否则会改变语义。
 *
 * **Kotlin 转换说明**：原 Java 为 package-private，Kotlin 用 `internal`；
 * 简单 getter 改为只读属性，位运算相关判断保持原样；引用比较一律用 `===`。
 */
internal class ArgsInfo(
	/** 本条指令 */
	val insn: InsnNode,
	/** 所在基本块的完整指令列表（按位置索引） */
	private val argsList: List<ArgsInfo>,
	/** 本条指令在块内的位置 */
	private val pos: Int,
) {
	/** 内联边界：允许把位置 >= inlineBorder 的指令搬走；初始为自己的位置 */
	private var inlineBorder: Int = pos

	/** 被内联的“父”指令（递归解析后会指向最外层） */
	private var inlinedInsn: ArgsInfo? = null

	/** 被内联进本条指令的指令列表 */
	private var wrappedInsns: MutableList<ArgsInfo>? = null

	/** 本条指令直接使用的寄存器参数（含嵌套 wrapped 指令里的） */
	val args: List<RegisterArg> = getArgs(insn)

	/** 本条指令直接/间接使用的所有寄存器编号集合；没有则返回共享空 BitSet。 */
	val argsSet: BitSet get() {
		if (args.isEmpty() && Utils.isEmpty(wrappedInsns)) {
			return EmptyBitSet.EMPTY
		}
		val set = BitSet()
		fillArgsSet(set)
		return set
	}

	private fun fillArgsSet(set: BitSet) {
		for (arg in args) {
			set.set(arg.regNum)
		}
		val wrapList = wrappedInsns
		if (wrapList != null) {
			for (wrappedInsn in wrapList) {
				wrappedInsn.fillArgsSet(set)
			}
		}
	}

	/** 尝试把位置 [assignPos] 的指令内联给参数 [arg]；不满足条件返回 null。 */
	fun checkInline(assignPos: Int, arg: RegisterArg): WrapInfo? {
		if (assignPos >= inlineBorder || !canMove(assignPos, inlineBorder)) {
			return null
		}
		inlineBorder = assignPos
		return inline(assignPos, arg)
	}

	/** 判断把 [from] 处指令搬到 [to] 处是否会破坏中间指令的读写依赖。 */
	private fun canMove(from: Int, to: Int): Boolean {
		val startInfo = argsList[from]
		val start = from + 1
		if (start == to) {
			// 相邻指令或正好在内联边界上，安全
			return true
		}
		if (start > to) {
			throw JadxRuntimeException("Invalid inline insn positions: $start - $to")
		}
		val movedSet = startInfo.argsSet
		if (movedSet === EmptyBitSet.EMPTY && startInfo.insn.isConstInsn) {
			return true
		}
		val canReorder = startInfo.canReorder()
		for (i in start until to) {
			val argsInfo = argsList[i]
			if (argsInfo.getInlinedInsn() === this) {
				continue
			}
			val curInsn = argsInfo.insn
			if (canReorder) {
				if (usedArgAssign(curInsn, movedSet)) {
					return false
				}
			} else {
				if (!curInsn.canReorder() || usedArgAssign(curInsn, movedSet)) {
					return false
				}
			}
		}
		return true
	}

	/** 本条指令（含其 wrapped 子指令）是否都可安全重排。 */
	private fun canReorder(): Boolean {
		if (!insn.canReorder()) {
			return false
		}
		val wrapList = wrappedInsns
		if (wrapList != null) {
			for (wrapInsn in wrapList) {
				if (!wrapInsn.canReorder()) {
					return false
				}
			}
		}
		return true
	}

	/** 执行内联：把 [assignInsnPos] 处指令挂到本条记录下，并返回包装信息。 */
	fun inline(assignInsnPos: Int, arg: RegisterArg): WrapInfo {
		val argsInfo = argsList[assignInsnPos]
		argsInfo.inlinedInsn = this
		var wrapList = wrappedInsns
		if (wrapList == null) {
			wrapList = ArrayList(args.size)
			wrappedInsns = wrapList
		}
		wrapList.add(argsInfo)
		return WrapInfo(argsInfo.insn, arg)
	}

	/** 返回最外层的被内联指令（路径压缩，避免重复向上查找）。 */
	fun getInlinedInsn(): ArgsInfo? {
		val cur = inlinedInsn
		if (cur != null) {
			val parent = cur.getInlinedInsn()
			if (parent != null) {
				inlinedInsn = parent
			}
		}
		return inlinedInsn
	}

	override fun toString(): String {
		val inlined = inlinedInsn
		return "ArgsInfo: |" + inlineBorder +
			" ->" + (if (inlined == null) "-" else inlined.pos.toString()) +
			' ' + args + " : " + insn
	}

	companion object {
		/** 收集一条指令（含嵌套 wrapped 指令、三元条件）使用的所有寄存器参数。 */
		fun getArgs(insn: InsnNode): List<RegisterArg> {
			val args = ArrayList<RegisterArg>()
			addArgs(insn, args)
			return args
		}

		private fun addArgs(insn: InsnNode, args: MutableList<RegisterArg>) {
			if (insn.type == InsnType.TERNARY) {
				args.addAll((insn as TernaryInsn).condition.registerArgs)
			}
			for (arg in insn.getArguments()) {
				if (arg.isRegister) {
					args.add(arg as RegisterArg)
				}
			}
			for (arg in insn.getArguments()) {
				if (arg.isInsnWrap) {
					addArgs((arg as InsnWrapArg).wrapInsn, args)
				}
			}
		}

		/** 指令的结果寄存器是否落在 [args] 集合中（用于判断读写冲突）。 */
		fun usedArgAssign(insn: InsnNode, args: BitSet): Boolean {
			if (args.isEmpty) {
				return false
			}
			val result = insn.result ?: return false
			return args.get(result.regNum)
		}
	}
}
