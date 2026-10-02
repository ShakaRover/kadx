package jadx.core.dex.visitors.ssa

import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.MethodNode
import java.util.Arrays

/**
 * SSA 重命名阶段的“当前寄存器版本状态”。
 *
 * **做什么**：在支配树上深度优先遍历基本块时，维护“每个寄存器当前对应哪个 SSA 变量（版本）”。
 * 每遇到一次对寄存器的赋值，就把该寄存器的版本号 +1 并生成新的 [SSAVar]。
 *
 * **为什么**：SSA（静态单赋值）要求每个变量只被赋值一次。用“版本号”区分同一寄存器的
 * 多次赋值，是构造 PHI 指令与后续数据流分析的基础。
 *
 * **结构**：
 * - [vars]：`寄存器号 -> 当前 SSA 变量`（进入某个块时的快照）；
 * - [versions]：`寄存器号 -> 下一个可用版本号`（在支配树分支间共享，保证版本号全局唯一）。
 *
 * **Kotlin 转换说明**：构造器私有，工厂方法 [init]/[copyFrom] 放在 `companion object`。
 * [copyFrom] 会复制 [vars] 数组（分支互不影响），但 [versions] 按引用共享（与原 Java 一致）。
 */
class RenameState private constructor(
	private val mth: MethodNode,
	private val block: BlockNode,
	private val vars: Array<SSAVar?>,
	private val versions: IntArray,
) {

	companion object {
		/** 初始化入口块的状态，并为 `this` 与所有参数寄存器建立初始 SSA 变量。 */
		@JvmStatic
		fun init(mth: MethodNode): RenameState {
			val regsCount = mth.getRegsCount()
			val state = RenameState(
				mth,
				checkNotNull(mth.enterBlock),
				arrayOfNulls(regsCount),
				IntArray(regsCount),
			)
			val thisArg = mth.getThisArg()
			if (thisArg != null) {
				state.startVar(thisArg)
			}
			for (arg in mth.getArgRegs()) {
				state.startVar(arg)
			}
			return state
		}

		/**
		 * 从已有状态派生进入另一个块的状态。
		 *
		 * [vars] 需要复制（不同支配分支各有一份），而 [versions] 共享同一数组，
		 * 这样同一寄存器在不同分支上也不会生成重复版本号。
		 */
		@JvmStatic
		fun copyFrom(state: RenameState, block: BlockNode): RenameState = RenameState(
			state.mth,
			block,
			Arrays.copyOf(state.vars, state.vars.size),
			state.versions,
		)
	}

	fun getBlock(): BlockNode = block

	fun getVar(regNum: Int): SSAVar? = vars[regNum]

	/** 为寄存器开启一个新版本，生成新的 [SSAVar] 并记录到当前状态。 */
	fun startVar(regArg: RegisterArg): SSAVar {
		val regNum = regArg.regNum
		val version = versions[regNum]++
		val ssaVar = mth.makeNewSVar(regNum, version, regArg)
		vars[regNum] = ssaVar
		return ssaVar
	}
}
