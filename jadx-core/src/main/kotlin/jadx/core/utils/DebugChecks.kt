package jadx.core.utils

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.PhiListAttr
import jadx.core.dex.attributes.nodes.TmpEdgeAttr
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.HashSet

/**
 * 检查块、指令、寄存器、SSA 变量之间的不变量与信息一致性。
 *
 * **用途**：这些检查非常昂贵，只在测试/调试模式下执行（见 [DebugChecksPass]）。
 */
object DebugChecks {

	private val IGNORE_CHECKS: Set<String> = HashSet(
		listOf(
			"PrepareForCodeGen",
			"RenameVisitor",
			"DotGraphVisitor",
		),
	)

	@JvmStatic
	fun insertPasses(passes: List<IDexTreeVisitor>): MutableList<IDexTreeVisitor> {
		val size = passes.size
		val list = ArrayList<IDexTreeVisitor>(size * 2)
		for (pass in passes) {
			list.add(pass)
			val name = pass.getName()
			if (!IGNORE_CHECKS.contains(name)) {
				list.add(DebugChecksPass(name))
			}
		}
		return list
	}

	@JvmStatic
	fun runChecksAfterVisitor(mth: MethodNode, visitor: String) {
		try {
			checkMethod(mth)
		} catch (e: Exception) {
			mth.addError("Debug check failed after visitor: $visitor", e)
		}
	}

	@JvmStatic
	fun checkMethod(mth: MethodNode) {
		val basicBlocks = mth.getBasicBlocks() ?: return
		if (basicBlocks.isEmpty()) {
			return
		}
		for (block in basicBlocks) {
			for (insn in block.getInstructions()) {
				checkInsn(mth, block, insn)
			}
		}
		checkSSAVars(mth)
		quickCheckPhiInsn(mth)
		// checkPHI(mth)
	}

	private fun checkInsn(mth: MethodNode, block: BlockNode, insn: InsnNode) {
		val res = insn.getResult()
		if (res != null) {
			checkVar(mth, insn, res)
		}
		for (arg in insn.getArguments()) {
			if (arg is RegisterArg) {
				checkVar(mth, insn, arg)
			} else if (arg is InsnWrapArg) {
				val wrapInsn = arg.wrapInsn
				if (wrapInsn.contains(AFlag.DONT_GENERATE) &&
					!insn.contains(AFlag.DONT_GENERATE) &&
					!mth.contains(AFlag.DONT_GENERATE)
				) {
					throw JadxRuntimeException("Not generated wrapped insn: \n $wrapInsn,\nouter insn:\n $insn")
				}
				if (wrapInsn.getResult() != null && !wrapInsn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
					throw JadxRuntimeException("Wrapped insn result should be removed: \n $wrapInsn,\nouter insn:\n $insn")
				}
				checkInsn(mth, block, wrapInsn)
			}
		}
		when (insn.getType()) {
			InsnType.TERNARY -> {
				val ternaryInsn = insn as TernaryInsn
				for (arg in ternaryInsn.getCondition().getRegisterArgs()) {
					checkVar(mth, insn, arg)
				}
			}

			InsnType.IF -> {
				val ifNode = insn as IfNode
				if (ifNode.getThenBlock() != ifNode.getElseBlock()) {
					// 排除临时边
					val branches = block.getSuccessors().stream().filter { b -> !hasTmpEdge(block, b) }.count().toInt()
					if (branches != 2) {
						DebugUtils.dumpRaw(mth, "error")
						throw JadxRuntimeException(
							"Incorrect if block successors count: $branches (expect 2), block: $block",
						)
					}
				}
				checkBlock(mth, checkNotNull(ifNode.getThenBlock())) { "then block in if insn: $ifNode" }
				checkBlock(mth, checkNotNull(ifNode.getElseBlock())) { "else block in if insn: $ifNode" }
			}

			else -> {}
		}
	}

	private fun hasTmpEdge(start: BlockNode, end: BlockNode): Boolean {
		val tmpEdgeAttr = end.get(AType.TMP_EDGE) ?: return false
		return tmpEdgeAttr.block == start
	}

	private fun checkBlock(mth: MethodNode, block: BlockNode, source: () -> String) {
		val basicBlocks = mth.getBasicBlocks()
		if (basicBlocks == null || !basicBlocks.contains(block)) {
			throw JadxRuntimeException("Block not registered in method: $block from " + source())
		}
	}

	private fun checkVar(mth: MethodNode, insn: InsnNode, reg: RegisterArg) {
		checkRegisterArg(mth, reg)

		val sVar = reg.sVar
		if (sVar == null) {
			if (reg.contains(AFlag.DONT_GENERATE) || insn.contains(AFlag.DONT_GENERATE)) {
				return
			}
			if (Utils.notEmpty(mth.getSVars())) {
				throw JadxRuntimeException("Null SSA var in $reg at $insn")
			}
			return
		}
		if (Utils.indexInListByRef(mth.getSVars(), sVar) == -1) {
			throw JadxRuntimeException("SSA var not present in method vars list, var: $sVar from insn: $insn")
		}
		val resArg = insn.getResult()
		val useList = sVar.getUseList()
		if (resArg === reg) {
			if (sVar.assignInsn !== insn) {
				throw JadxRuntimeException(
					"Incorrect assign in ssa var: $sVar\n expected: " + sVar.assignInsn + "\n got: " + insn,
				)
			}
		} else {
			if (!Utils.containsInListByRef(useList, reg)) {
				throw JadxRuntimeException("Incorrect use list in ssa var: $sVar, register not listed.\n insn: $insn")
			}
		}
		for (useArg in useList) {
			checkRegisterArg(mth, useArg)
		}
	}

	private fun checkSSAVars(mth: MethodNode) {
		for (ssaVar in mth.getSVars()) {
			val assignArg = ssaVar.assign
			if (assignArg.contains(AFlag.REMOVE)) {
				// 忽略已删除的变量
				continue
			}
			val assignInsn = assignArg.getParentInsn()
			if (assignInsn != null) {
				if (insnMissing(mth, assignInsn)) {
					throw JadxRuntimeException("Insn not found for assign arg in SSAVar: $ssaVar, insn: $assignInsn")
				}
				val resArg = assignInsn.getResult()
				if (resArg == null) {
					throw JadxRuntimeException("SSA assign insn result missing. SSAVar: $ssaVar, insn: $assignInsn")
				}
				val assignVar = resArg.sVar
				if (assignVar != ssaVar) {
					throw JadxRuntimeException(
						"Unexpected SSAVar in assign. Expected: $ssaVar, got: $assignVar, insn: $assignInsn",
					)
				}
			}
			for (arg in ssaVar.getUseList()) {
				val useInsn = arg.getParentInsn()
				if (useInsn == null) {
					throw JadxRuntimeException("Parent insn can't be null for arg in use list of SSAVar: $ssaVar")
				}
				if (insnMissing(mth, useInsn)) {
					throw JadxRuntimeException("Insn not found for use arg for SSAVar: $ssaVar, insn: $useInsn")
				}
				val argIndex = useInsn.getArgIndex(arg)
				if (argIndex == -1) {
					throw JadxRuntimeException("Use arg not found in insn for SSAVar: $ssaVar, insn: $useInsn")
				}
				val foundArg = useInsn.getArg(argIndex)
				if (foundArg != arg) {
					throw JadxRuntimeException(
						"Incorrect use arg in insn for SSAVar: $ssaVar, insn: $useInsn, arg: $foundArg",
					)
				}
			}
		}
	}

	private fun insnMissing(mth: MethodNode, insn: InsnNode): Boolean {
		if (insn.contains(AFlag.HIDDEN)) {
			// 跳过查找
			return false
		}
		val block = BlockUtils.getBlockByInsn(mth, insn)
		return block == null
	}

	private fun checkRegisterArg(mth: MethodNode, reg: RegisterArg) {
		val parentInsn = reg.getParentInsn()
		if (parentInsn == null) {
			if (reg.contains(AFlag.METHOD_ARGUMENT)) {
				return
			}
			throw JadxRuntimeException("Null parentInsn for reg: $reg")
		}
		if (!parentInsn.contains(AFlag.HIDDEN)) {
			if (parentInsn.getResult() !== reg && !parentInsn.containsArg(reg)) {
				throw JadxRuntimeException("Incorrect parentInsn: $parentInsn, must contains arg: $reg")
			}
			val parentInsnBlock = BlockUtils.getBlockByInsn(mth, parentInsn)
			if (parentInsnBlock == null) {
				throw JadxRuntimeException("Parent insn not found in blocks tree for: $reg\n insn: $parentInsn")
			}
		}
	}

	@JvmStatic
	fun quickCheckPhiInsn(mth: MethodNode) {
		if (mth.getSVars().isEmpty()) {
			return
		}
		for (block in checkNotNull(mth.getBasicBlocks())) {
			val phiListAttr = block.get(AType.PHI_LIST)
			if (phiListAttr != null) {
				for (phiInsn in phiListAttr.list) {
					checkPhiArg(mth, phiInsn, phiInsn.getResult()) { "result" }
					val argsCount = phiInsn.getArgsCount()
					for (i in 0 until argsCount) {
						val argNum = i
						checkPhiArg(mth, phiInsn, phiInsn.getArg(argNum)) { "arg_$argNum" }
					}
				}
			}
		}
	}

	private fun checkPhiArg(mth: MethodNode, phiInsn: PhiInsn, arg: RegisterArg?, argName: () -> String) {
		if (arg == null) {
			throw JadxRuntimeException("Null " + argName() + " in PHI insn: " + phiInsn)
		}
		if (arg.sVar == null) {
			throw JadxRuntimeException("Null SSA variable in " + argName() + " in PHI insn: " + phiInsn)
		}
	}

	@Suppress("unused")
	private fun checkPHI(mth: MethodNode) {
		for (block in checkNotNull(mth.getBasicBlocks())) {
			val phis = ArrayList<PhiInsn>()
			for (insn in block.getInstructions()) {
				if (insn.getType() == InsnType.PHI) {
					val phi = insn as PhiInsn
					phis.add(phi)
					if (phi.getArgsCount() == 0) {
						throw JadxRuntimeException("No args and binds in PHI")
					}
					for (arg in insn.getArguments()) {
						if (arg is RegisterArg) {
							val b = phi.getBlockByArg(arg)
							if (b == null) {
								throw JadxRuntimeException("Predecessor block not found")
							}
						} else {
							throw JadxRuntimeException("Not register in phi insn")
						}
					}
				}
			}
			val phiListAttr = block.get(AType.PHI_LIST)
			if (phiListAttr == null) {
				if (phis.isNotEmpty()) {
					throw JadxRuntimeException("Missing PHI list attribute")
				}
			} else {
				val phiList = phiListAttr.list
				if (phiList.isEmpty()) {
					throw JadxRuntimeException("Empty PHI list attribute")
				}
				if (!phis.containsAll(phiList) || !phiList.containsAll(phis)) {
					throw JadxRuntimeException("Instructions not match")
				}
			}
		}
		for (ssaVar in mth.getSVars()) {
			for (usedInPhi in ssaVar.getUsedInPhi()) {
				var found = false
				for (useArg in ssaVar.getUseList()) {
					val parentInsn = useArg.getParentInsn()
					if (parentInsn != null && parentInsn === usedInPhi) {
						found = true
						break
					}
				}
				if (!found) {
					throw JadxRuntimeException("Used in phi incorrect")
				}
			}
		}
	}
}
