package jadx.core.dex.instructions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.InsnNode
import jadx.core.utils.InsnRemover
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * PHI 指令：SSA（静态单赋值）形式中用于“按前驱基本块合并多个变量版本”的伪指令。
 *
 * 例如控制流汇合处 `x = (来自块 A 的 x1) 或 (来自块 B 的 x2)` 就表示为一个 PHI。
 * 它的每个参数都与一个前驱块一一对应（[blockBinds]），因此不允许直接 addArg / setArg，
 * 必须使用 [bindArg] 保持参数与块的对应关系。
 *
 * Kotlin 转换说明：参数与块的绑定关系是核心不变量，相关方法保持原 Java 方法名与语义。
 */
class PhiInsn : InsnNode {

	// 与参数列表顺序一一对应的前驱块列表
	private val blockBinds: MutableList<BlockNode>

	constructor(regNum: Int, predecessors: Int) : this(predecessors) {
		setResult(InsnArg.reg(regNum, ArgType.UNKNOWN))
		add(AFlag.DONT_INLINE)
		add(AFlag.DONT_GENERATE)
	}

	private constructor(argsCount: Int) : super(InsnType.PHI, argsCount) {
		this.blockBinds = ArrayList(argsCount)
	}

	/** 为前驱块 [pred] 创建一个新的寄存器参数并绑定。 */
	fun bindArg(pred: BlockNode): RegisterArg {
		val result = checkNotNull(getResult())
		val arg = InsnArg.reg(result.regNum, result.getInitType())
		bindArg(arg, pred)
		return arg
	}

	/** 把已有参数 [arg] 绑定到前驱块 [pred]。 */
	fun bindArg(arg: RegisterArg, pred: BlockNode?) {
		if (pred != null && blockBinds.contains(pred)) {
			throw JadxRuntimeException("Duplicate predecessors in PHI insn: $pred, $this")
		}
		if (pred == null) {
			throw JadxRuntimeException("Null bind block in PHI insn: $this")
		}
		super.addArg(arg)
		blockBinds.add(pred)
	}

	fun getBlockByArg(arg: RegisterArg): BlockNode? {
		val index = getArgIndex(arg)
		if (index == -1) {
			return null
		}
		return blockBinds[index]
	}

	fun getBlockByArgIndex(argIndex: Int): BlockNode = blockBinds[argIndex]

	override fun getArg(n: Int): RegisterArg = super.getArg(n) as RegisterArg

	/** 按“引用相等”查找参数对应的块（与原 Java 的 `==` 语义一致）。 */
	fun getArgByBlock(block: BlockNode): RegisterArg? {
		for (i in blockBinds.indices) {
			if (blockBinds[i] === block) {
				return getArg(i)
			}
		}
		return null
	}

	public override fun removeArg(arg: InsnArg): Boolean {
		val index = getArgIndex(arg)
		if (index == -1) {
			return false
		}
		removeArg(index)
		return true
	}

	override fun removeArg(index: Int): RegisterArg {
		val reg = super.removeArg(index) as RegisterArg
		blockBinds.removeAt(index)
		checkNotNull(reg.sVar).updateUsedInPhiList()
		return reg
	}

	fun getArgBySsaVar(ssaVar: SSAVar): RegisterArg? {
		if (getArgsCount() == 0) {
			return null
		}
		for (insnArg in getArguments()) {
			val reg = insnArg as RegisterArg
			if (reg.sVar === ssaVar) {
				return reg
			}
		}
		return null
	}

	/** 按 [IBlock] 查找参数；这里沿用原 Java 的 equals 匹配语义。 */
	fun getArgByBlock(block: IBlock): RegisterArg? {
		if (getArgsCount() == 0) {
			return null
		}
		val index = blockBinds.indexOfFirst { it == block }
		if (index == -1) {
			return null
		}
		return getArg(index)
	}

	override fun replaceArg(from: InsnArg, to: InsnArg): Boolean {
		if (from !is RegisterArg || to !is RegisterArg) {
			return false
		}
		val argIndex = getArgIndex(from)
		if (argIndex == -1) {
			return false
		}
		checkNotNull(to.sVar).addUsedInPhi(this)
		super.setArg(argIndex, to)

		InsnRemover.unbindArgUsage(null, from)
		checkNotNull(from.sVar).updateUsedInPhiList()
		return true
	}

	override fun addArg(arg: InsnArg): Unit = throw JadxRuntimeException("Direct addArg is forbidden for PHI insn, bindArg must be used")

	override fun setArg(n: Int, arg: InsnArg): Unit = throw JadxRuntimeException("Direct setArg is forbidden for PHI insn, bindArg must be used")

	override fun copy(): InsnNode = copyCommonParams(PhiInsn(getArgsCount()))

	override fun toString(): String = baseString() + " binds: " + blockBinds + attributesString()
}
