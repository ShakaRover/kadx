package jadx.core.dex.visitors.ssa

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.PhiListAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.blocks.BlockProcessor
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnList
import jadx.core.utils.InsnRemover
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.BitSet

/**
 * SSA（Static Single Assignment，静态单赋值）变换。
 *
 * **目标**：把“同一寄存器被多次赋值”的普通代码，改造成“每个变量只赋值一次”的形式：
 * 为每次赋值创建独立的 [SSAVar]（带版本号），在控制流汇合处插入 PHI 指令。
 *
 * **整体流程**（[process]）：
 * 1. 活跃变量分析 [LiveVarAnalysis]，得到每个寄存器的赋值块；
 * 2. 对每个寄存器，在其赋值块的支配边界处放置 PHI（[placePhi]）；
 * 3. 沿支配树重命名变量，绑定每个 PHI 参数到对应前驱的版本（[renameVariables]）；
 * 4. 处理 try/catch 边界、清理无用 PHI、标记 `this`、隐藏 PHI 等收尾工作。
 *
 * **Kotlin 转换说明**：`addPhi` 被 Java 调用方（ConstructorVisitor）静态调用，
 * 放入 `companion object` 并加 `@JvmStatic`；块/SSAVar/RegisterArg 的身份判断一律用 `===`。
 */
@JadxVisitor(
	name = "SSATransform",
	desc = "Calculate Single Side Assign (SSA) variables",
	runAfter = [BlockProcessor::class],
)
class SSATransform : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		process(mth)
	}

	private fun process(mth: MethodNode) {
		if (mth.getSVars().isNotEmpty()) {
			return
		}
		val la = LiveVarAnalysis(mth)
		la.runAnalysis()
		val regsCount = mth.getRegsCount()
		for (i in 0 until regsCount) {
			placePhi(mth, i, la)
		}
		renameVariables(mth)
		fixLastAssignInTry(mth)
		removeBlockerInsns(mth)
		tryToFixUselessPhi(mth)
		markThisArgs(mth.getThisArg())
		hidePhiInsns(mth)
		removeUnusedInvokeResults(mth)
	}

	/**
	 * 为寄存器 [regNum] 放置 PHI：从它的每个赋值块出发，沿支配边界迭代扩散，
	 * 在“活跃”的边界块插入 PHI。
	 */
	private fun placePhi(mth: MethodNode, regNum: Int, la: LiveVarAnalysis) {
		val blocks = checkNotNull(mth.getBasicBlocks())
		val blocksCount = blocks.size
		val hasPhi = BitSet(blocksCount)
		val processed = BitSet(blocksCount)
		val workList = ArrayDeque<BlockNode>()

		val assignBlocks = la.getAssignBlocks(regNum)
		var id = assignBlocks.nextSetBit(0)
		while (id >= 0) {
			processed.set(id)
			workList.add(blocks[id])
			id = assignBlocks.nextSetBit(id + 1)
		}
		while (!workList.isEmpty()) {
			val block = workList.pop()
			val domFrontier = checkNotNull(block.domFrontier)
			var dfId = domFrontier.nextSetBit(0)
			while (dfId >= 0) {
				if (!hasPhi.get(dfId) && la.isLive(dfId, regNum)) {
					val df = blocks[dfId]
					val phiInsn = addPhi(mth, df, regNum)
					df.instructions.add(0, phiInsn)
					hasPhi.set(dfId)
					if (!processed.get(dfId)) {
						processed.set(dfId)
						workList.add(df)
					}
				}
				dfId = domFrontier.nextSetBit(dfId + 1)
			}
		}
	}

	companion object {
		/**
		 * 在 [block] 上创建一条 PHI 指令并登记到块的 PHI 列表。
		 *
		 * PHI 参数个数等于前驱个数；若该块是方法入口块，还要为 `this`/参数寄存器多留一个槽位。
		 */
		@JvmStatic
		fun addPhi(mth: MethodNode, block: BlockNode, regNum: Int): PhiInsn {
			var phiList = block.get(AType.PHI_LIST)
			if (phiList == null) {
				phiList = PhiListAttr()
				block.addAttr(phiList)
			}
			var size = block.getPredecessors().size
			if (mth.enterBlock === block) {
				val thisArg = mth.getThisArg()
				if (thisArg != null && thisArg.regNum == regNum) {
					size++
				} else {
					for (arg in mth.getArgRegs()) {
						if (arg.regNum == regNum) {
							size++
							break
						}
					}
				}
			}
			val phiInsn = PhiInsn(regNum, size)
			phiList.list.add(phiInsn)
			phiInsn.setOffset(block.startOffset)
			return phiInsn
		}
	}

	/**
	 * 沿支配树自顶向下重命名变量：为每个块维护一份寄存器版本快照，
	 * 每个块执行完再把快照复制给它的直接支配子节点。
	 */
	private fun renameVariables(mth: MethodNode) {
		val initState = RenameState.init(mth)
		initPhiInEnterBlock(initState)

		val stack = ArrayDeque<RenameState>()
		stack.push(initState)
		while (!stack.isEmpty()) {
			val state = stack.pop()
			renameVarsInBlock(mth, state)
			for (dominated in state.getBlock().getDominatesOn()) {
				stack.push(RenameState.copyFrom(state, dominated))
			}
		}
	}

	/** 入口块可能自带 PHI（例如参数/`this` 在入口的合流），先绑定它们的参数。 */
	private fun initPhiInEnterBlock(initState: RenameState) {
		val phiList = initState.getBlock().get(AType.PHI_LIST)
		if (phiList != null) {
			for (phiInsn in phiList.list) {
				bindPhiArg(initState, phiInsn)
			}
		}
	}

	/**
	 * 重命名一个块内的变量。
	 *
	 * 对块内每条指令：非 PHI 指令的寄存器参数绑定当前版本的 SSA 变量；
	 * 有结果的指令开启新版本；最后把本块版本绑定到后继块的 PHI 参数上。
	 */
	private fun renameVarsInBlock(mth: MethodNode, state: RenameState) {
		val block = state.getBlock()
		for (insn in block.getInstructions()) {
			if (insn.getType() != InsnType.PHI) {
				for (arg in insn.getArguments()) {
					if (!arg.isRegister) {
						continue
					}
					val reg = arg as RegisterArg
					val regNum = reg.regNum
					var v = state.getVar(regNum)
					if (v == null) {
						// 多数情况是异常处理器连接不正确导致的
						mth.addWarnComment("Not initialized variable reg: $regNum, insn: $insn, block:$block")
						v = state.startVar(reg)
					}
					v.use(reg)
				}
			}
			val result = insn.getResult()
			if (result != null) {
				state.startVar(result)
			}
		}
		for (s in block.getSuccessors()) {
			val phiList = s.get(AType.PHI_LIST)
			if (phiList == null) {
				continue
			}
			for (phiInsn in phiList.list) {
				bindPhiArg(state, phiInsn)
			}
		}
	}

	/** 把当前块的版本绑定到 PHI 参数（PHI 的参数来自不同前驱）。 */
	private fun bindPhiArg(state: RenameState, phiInsn: PhiInsn) {
		val regNum = checkNotNull(phiInsn.getResult()).regNum
		val v = state.getVar(regNum) ?: return
		val arg = phiInsn.bindArg(state.getBlock())
		v.use(arg)
		v.addUsedInPhi(phiInsn)
	}

	/**
	 * 修复 try/catch 边界上的最后一个赋值：在异常处理器里的 PHI 应去掉
	 * 那些“从 try 中带异常离开”的赋值参数。
	 */
	private fun fixLastAssignInTry(mth: MethodNode) {
		for (block in checkNotNull(mth.getBasicBlocks())) {
			val phiList = block.get(AType.PHI_LIST)
			if (phiList != null) {
				val handlerAttr = block.get(AType.EXC_HANDLER)
				if (handlerAttr != null) {
					for (phi in phiList.list) {
						fixPhiInTryCatch(mth, phi, handlerAttr)
					}
				}
			}
		}
	}

	private fun fixPhiInTryCatch(mth: MethodNode, phi: PhiInsn, handlerAttr: ExcHandlerAttr) {
		var argsCount = phi.getArgsCount()
		var k = 0
		while (k < argsCount) {
			val arg = phi.getArg(k)
			if (shouldSkipInsnResult(mth, arg.getAssignInsn(), handlerAttr)) {
				phi.removeArg(arg)
				argsCount--
			} else {
				k++
			}
		}
		if (phi.getArgsCount() == 0) {
			throw JadxRuntimeException("PHI empty after try-catch fix!")
		}
	}

	private fun shouldSkipInsnResult(mth: MethodNode, insn: InsnNode?, handlerAttr: ExcHandlerAttr): Boolean {
		if (insn != null &&
			insn.getResult() != null &&
			insn.contains(AFlag.TRY_LEAVE)
		) {
			val catchAttr = BlockUtils.getCatchAttrForInsn(mth, insn)
			return catchAttr != null && catchAttr.getHandlers().contains(handlerAttr.getHandler())
		}
		return false
	}

	/**
	 * 移除“阻塞” PHI 的指令：若 PHI 参数对应的赋值指令被标记为 REMOVE，
	 * 则同时从 PHI 中删掉该参数并移除该指令。
	 */
	private fun removeBlockerInsns(mth: MethodNode): Boolean {
		var removed = false
		for (block in checkNotNull(mth.getBasicBlocks())) {
			val phiList = block.get(AType.PHI_LIST) ?: continue
			for (phi in phiList.list) {
				var i = 0
				while (i < phi.getArgsCount()) {
					val arg = phi.getArg(i)
					val parentInsn = arg.getAssignInsn()
					if (parentInsn != null && parentInsn.contains(AFlag.REMOVE)) {
						phi.removeArg(arg)
						InsnRemover.remove(mth, block, parentInsn)
						removed = true
					}
					i++
				}
			}
		}
		return removed
	}

	/** 反复清理无用 PHI，直到不再变化（带迭代上限保护）。 */
	private fun tryToFixUselessPhi(mth: MethodNode) {
		var k = 0
		val maxTries = mth.getSVars().size * 2
		while (fixUselessPhi(mth)) {
			if (k++ > maxTries) {
				throw JadxRuntimeException("Phi nodes fix limit reached!")
			}
		}
	}

	/**
	 * 一轮无用 PHI 清理：
	 * - 结果从未被使用的 PHI；
	 * - 参数全相同（可退化成 move）的 PHI。
	 */
	private fun fixUselessPhi(mth: MethodNode): Boolean {
		var changed = false
		val insnToRemove = ArrayList<PhiInsn>()
		for (v in mth.getSVars()) {
			// 结果未被使用的 PHI
			if (v.getUseCount() == 0) {
				val assignInsn = v.assign.getParentInsn()
				if (assignInsn != null && assignInsn.getType() == InsnType.PHI) {
					insnToRemove.add(assignInsn as PhiInsn)
					changed = true
				}
			}
		}
		for (block in checkNotNull(mth.getBasicBlocks())) {
			val phiList = block.get(AType.PHI_LIST) ?: continue
			val it = phiList.list.iterator()
			while (it.hasNext()) {
				val phi = it.next()
				if (fixPhiWithSameArgs(mth, block, phi)) {
					it.remove()
					changed = true
				}
			}
		}
		removePhiList(mth, insnToRemove)
		return changed
	}

	/** 处理“参数全相同”的 PHI：尽量内联成 move，否则删除。 */
	private fun fixPhiWithSameArgs(mth: MethodNode, block: BlockNode, phi: PhiInsn): Boolean {
		if (phi.getArgsCount() == 0) {
			val resultVar = checkNotNull(checkNotNull(phi.getResult()).sVar)
			for (useArg in resultVar.getUseList()) {
				val useInsn = useArg.getParentInsn()
				if (useInsn != null && useInsn.getType() == InsnType.PHI) {
					phi.removeArg(useArg)
				}
			}
			InsnRemover.remove(mth, block, phi)
			return true
		}
		val allSame = phi.getArgsCount() == 1 || isSameArgs(phi)
		if (allSame) {
			return replacePhiWithMove(mth, block, phi, phi.getArg(0))
		}
		val sameVar = isSameMove(phi)
		if (sameVar != null) {
			val sameArg = sameVar.assign.duplicate()
			if (inlinePhiInsn(mth, block, phi, sameArg)) {
				for (arg in phi.getArguments()) {
					val moveInsn = (arg as RegisterArg).getAssignInsn()
					if (moveInsn != null) {
						moveInsn.add(AFlag.REMOVE)
						InsnRemover.remove(mth, moveInsn)
					}
				}
				return true
			}
		}
		return false
	}

	/** 判断 PHI 的所有参数是否指向同一个 SSA 变量。 */
	private fun isSameArgs(phi: PhiInsn): Boolean {
		var allSame = true
		var v: SSAVar? = null
		for (i in 0 until phi.getArgsCount()) {
			val arg = phi.getArg(i)
			if (v == null) {
				v = arg.sVar
			} else if (v !== arg.sVar) {
				allSame = false
				break
			}
		}
		return allSame
	}

	/**
	 * 判断 PHI 的每个参数是否都来自“同一条只使用一次的 move 指令”。
	 * 若是，则可以把这个 PHI 内联成对该 move 源变量的直接引用。
	 */
	private fun isSameMove(phi: PhiInsn): SSAVar? {
		var v: SSAVar? = null
		val argsCount = phi.getArgsCount()
		for (i in 0 until argsCount) {
			val arg = phi.getArg(i)
			if (checkNotNull(arg.sVar).getUseCount() != 1) {
				return null
			}
			val assignInsn = arg.getAssignInsn()
			if (assignInsn == null || assignInsn.getType() != InsnType.MOVE) {
				return null
			}
			val moveArg = assignInsn.getArg(0)
			if (!moveArg.isRegister) {
				return null
			}
			val moveVar = (moveArg as RegisterArg).sVar
			if (v == null) {
				v = moveVar
			} else if (v !== moveVar) {
				return null
			}
		}
		return v
	}

	private fun removePhiList(mth: MethodNode, insnToRemove: MutableList<PhiInsn>): Boolean {
		for (block in checkNotNull(mth.getBasicBlocks())) {
			val phiList = block.get(AType.PHI_LIST) ?: continue
			val list = phiList.list
			for (phiInsn in insnToRemove) {
				if (list.remove(phiInsn)) {
					for (arg in phiInsn.getArguments()) {
						if (arg == null) {
							continue
						}
						val sVar = (arg as RegisterArg).sVar
						if (sVar != null) {
							sVar.removeUsedInPhi(phiInsn)
						}
					}
					InsnRemover.remove(mth, block, phiInsn)
				}
			}
			if (list.isEmpty()) {
				block.remove(AType.PHI_LIST)
			}
		}
		insnToRemove.clear()
		return true
	}

	/**
	 * 把 PHI 替换成一条 move 指令（当所有参数等价时）。
	 * 若还能进一步内联，则直接删除 PHI。
	 */
	private fun replacePhiWithMove(mth: MethodNode, block: BlockNode, phi: PhiInsn, arg: RegisterArg): Boolean {
		val insns = block.instructions
		val phiIndex = InsnList.getIndex(insns, phi)
		if (phiIndex == -1) {
			return false
		}
		val assign = checkNotNull(phi.getResult()).sVar
		val argVar = arg.sVar
		if (argVar != null) {
			argVar.removeUse(arg)
			argVar.removeUsedInPhi(phi)
		}
		// 尝试直接内联
		if (inlinePhiInsn(mth, block, phi, phi.getArg(0))) {
			insns.removeAt(phiIndex)
		} else {
			checkNotNull(assign).removeUsedInPhi(phi)

			val m = InsnNode(InsnType.MOVE, 1)
			m.add(AFlag.SYNTHETIC)
			m.setResult(phi.getResult())
			m.addArg(arg)
			checkNotNull(arg.sVar).use(arg)
			insns[phiIndex] = m
		}
		return true
	}

	/**
	 * 把 PHI 的所有使用点直接替换为 [inlineArg]，并解绑 PHI。
	 * 若存在无法替换的使用点（或使用点就是 PHI 自身），则放弃内联。
	 */
	private fun inlinePhiInsn(mth: MethodNode, block: BlockNode, phi: PhiInsn, inlineArg: RegisterArg): Boolean {
		val resVar = checkNotNull(phi.getResult()).sVar ?: return false
		val inlineSVar = inlineArg.sVar ?: return false
		val useList = resVar.getUseList()
		for (useArg in ArrayList(useList)) {
			val useInsn = useArg.getParentInsn()
			if (useInsn == null || useInsn === phi) {
				return false
			}
			if (useArg.regNum == inlineArg.regNum) {
				// 寄存器号相同：只需换绑 SSA 变量，不必替换整个 RegisterArg
				checkNotNull(useArg.sVar).removeUse(useArg)
				inlineSVar.use(useArg)
			} else {
				if (!useInsn.replaceArg(useArg, inlineArg)) {
					return false
				}
			}
		}
		if (block.contains(AType.EXC_HANDLER)) {
			// 不要内联进异常处理器
			val assignInsn = inlineArg.getAssignInsn()
			if (assignInsn != null && !assignInsn.isConstInsn()) {
				assignInsn.add(AFlag.DONT_INLINE)
			}
		}
		InsnRemover.unbindInsn(mth, phi)
		return true
	}

	/** 标记 `this` 参数及其所有使用点为 THIS，供后续代码生成识别。 */
	private fun markThisArgs(thisArg: RegisterArg?) {
		if (thisArg != null) {
			markOneArgAsThis(thisArg)
			for (arg in checkNotNull(thisArg.sVar).getUseList()) {
				markOneArgAsThis(arg)
			}
		}
	}

	private fun markOneArgAsThis(arg: RegisterArg?) {
		if (arg == null) {
			return
		}
		arg.add(AFlag.THIS)
		arg.add(AFlag.IMMUTABLE_TYPE)
		// 标记所有“被移动过的 this”
		val parentInsn = arg.getParentInsn()
		if (parentInsn != null &&
			parentInsn.getType() == InsnType.MOVE &&
			parentInsn.getArg(0) === arg
		) {
			val resArg = checkNotNull(parentInsn.getResult())
			if (resArg.regNum != arg.regNum && !checkNotNull(resArg.sVar).isUsedInPhi()) {
				markThisArgs(resArg)
				parentInsn.add(AFlag.DONT_GENERATE)
			}
		}
	}

	/** PHI 只用于分析，不参与代码生成，完成后从指令列表中移除。 */
	private fun hidePhiInsns(mth: MethodNode) {
		for (block in checkNotNull(mth.getBasicBlocks())) {
			block.instructions.removeIf { insn -> insn.getType() == InsnType.PHI }
		}
	}

	/** 结果未被使用的 invoke 指令，直接去掉其结果（避免生成无意义的变量）。 */
	private fun removeUnusedInvokeResults(mth: MethodNode) {
		for (ssaVar in ArrayList(mth.getSVars())) {
			if (ssaVar.getUseCount() == 0) {
				val parentInsn = ssaVar.assign.getParentInsn()
				if (parentInsn != null && parentInsn.getType() == InsnType.INVOKE) {
					parentInsn.setResult(null)
					mth.removeSVar(ssaVar)
				}
			}
		}
	}
}
