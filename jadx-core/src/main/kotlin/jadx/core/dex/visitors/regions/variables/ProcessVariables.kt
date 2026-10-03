package jadx.core.dex.visitors.regions.variables

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.DeclareVariablesAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.regions.AbstractRegionVisitor
import jadx.core.dex.visitors.regions.DepthRegionTraversal
import jadx.core.dex.visitors.typeinference.TypeCompareEnum
import jadx.core.utils.ListUtils
import jadx.core.utils.RegionUtils
import jadx.core.utils.Utils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 决定局部变量的声明位置，并把声明信息挂到对应区域上。
 *
 * **算法意图**：反编译输出里局部变量要在合适的区域开头声明。本 Pass：
 * 1. [removeUnusedResults]：先删掉结果从未被使用的赋值（或整条无用指令）；
 * 2. [collectCodeVars]：把同一源码变量的多个 SSA 变量归并到一个 [CodeVar]；
 * 3. [CollectUsageRegionVisitor] 收集每个 SSA 变量的赋值/使用位置；
 * 4. [declareVar]：优先尝试在某个赋值点就地声明，否则退化为在方法起始区域声明。
 *
 * Kotlin 转换说明：
 * - 原 Java 的 `==` 对象比较改为 `===`；
 * - `CodeVar` 已是 Kotlin 属性（`type`/`ssaVars`/`isDeclared`），改用属性语法；
 * - 热点遍历保持普通 `for` 循环。
 */
class ProcessVariables : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() || mth.SVars.isEmpty()) {
			return
		}
		removeUnusedResults(mth)

		val codeVars = collectCodeVars(mth)
		if (codeVars.isEmpty()) {
			return
		}
		checkCodeVars(mth, codeVars)

		// 收集所有变量使用情况
		val usageCollector = CollectUsageRegionVisitor()
		DepthRegionTraversal.traverse(mth, usageCollector)
		val ssaUsageMap = usageCollector.usageMap
		if (ssaUsageMap.isEmpty()) {
			return
		}

		val codeVarUsage = mergeUsageMaps(codeVars, ssaUsageMap)
		for ((codeVar, usageList) in codeVarUsage) {
			declareVar(mth, codeVar, usageList)
		}
	}

	/** 删除结果从未被使用的赋值（或整条无用指令） */
	private fun removeUnusedResults(mth: MethodNode) {
		DepthRegionTraversal.traverse(
			mth,
			object : AbstractRegionVisitor() {
				override fun processBlock(mth: MethodNode, container: IBlock) {
					for (insn in container.instructions) {
						val resultArg = insn.result ?: continue
						val ssaVar = resultArg.sVar
						if (isVarUnused(mth, ssaVar)) {
							var remove = false
							if (insn.canRemoveResult()) {
								// 删除未使用的结果
								remove = true
							} else if (canRemoveInsn(insn)) {
								// 删除整条指令
								insn.add(AFlag.REMOVE)
								insn.add(AFlag.DONT_GENERATE)
								remove = true
							}
							if (remove) {
								insn.setResult(null)
								val sv = checkNotNull(ssaVar)
								mth.removeSVar(sv)
								for (arg in sv.useList) {
									arg.resetSSAVar()
								}
							}
						}
					}
				}

				/** 若结果未使用，整条指令可以删除 */
				private fun canRemoveInsn(insn: InsnNode): Boolean {
					if (insn.isConstInsn) {
						return true
					}
					return when (insn.type) {
						InsnType.CAST, InsnType.CHECK_CAST -> true
						else -> false
					}
				}

				private fun isVarUnused(mth: MethodNode, ssaVar: SSAVar?): Boolean {
					if (ssaVar == null) {
						return true
					}
					val useList = ssaVar.useList
					if (useList.isEmpty()) {
						return true
					}
					if (ssaVar.isUsedInPhi()) {
						return false
					}
					return ListUtils.allMatch(useList) { isArgUnused(mth, it) }
				}

				private fun isArgUnused(mth: MethodNode, arg: RegisterArg): Boolean {
					if (arg.contains(AFlag.REMOVE)) {
						return true
					}
					// 检查构造器中已被删除的参数
					val parentInsn = arg.getParentInsn()
					if (parentInsn != null &&
						parentInsn.type == InsnType.CONSTRUCTOR &&
						parentInsn.contains(AType.METHOD_DETAILS)
					) {
						val resolveMth = mth.root().getMethodUtils().resolveMethod(parentInsn as ConstructorInsn)
						if (resolveMth != null && resolveMth.contains(AType.SKIP_MTH_ARGS)) {
							val insnPos = parentInsn.getArgIndex(arg)
							val mthArgs = resolveMth.argRegs
							if (0 <= insnPos && insnPos < mthArgs.size) {
								val mthArg = mthArgs[insnPos]
								if (mthArg.contains(AFlag.REMOVE) && arg.sameType(mthArg)) {
									arg.add(AFlag.DONT_GENERATE)
									return true
								}
							}
						}
					}
					return false
				}
			},
		)
	}

	/** 校验并修正 CodeVar 的类型（未知类型记为 UNKNOWN，并统计告警） */
	private fun checkCodeVars(mth: MethodNode, codeVars: List<CodeVar>) {
		var unknownTypesCount = 0
		for (codeVar in codeVars) {
			val codeVarType = codeVar.type
			if (codeVarType == null) {
				codeVar.type = ArgType.UNKNOWN
				unknownTypesCount++
			} else {
				for (ssaVar in codeVar.ssaVars) {
					val ssaType = ssaVar.immutableType
					if (ssaType != null && ssaType.isTypeKnown()) {
						val comparator = mth.root().typeUpdate.typeCompare
						val result = comparator.compareTypes(ssaType, codeVarType)
						if (result == TypeCompareEnum.CONFLICT || result.isNarrow()) {
							mth.addWarn(
								"Incorrect type for immutable var: ssa=" + ssaType +
									", code=" + codeVarType +
									", for " + ssaVar.getDetailedVarInfo(mth),
							)
						}
					}
				}
			}
		}
		if (unknownTypesCount != 0) {
			mth.addWarn("Unknown variable types count: " + unknownTypesCount)
		}
	}

	/** 尝试在赋值点声明变量；失败则在方法起始区域声明 */
	private fun declareVar(mth: MethodNode, codeVar: CodeVar, usageList: List<VarUsage>) {
		if (codeVar.isDeclared) {
			return
		}

		val mergedUsage = VarUsage(null)
		for (varUsage in usageList) {
			mergedUsage.assigns.addAll(varUsage.assigns)
			mergedUsage.uses.addAll(varUsage.uses)
		}
		if (mergedUsage.assigns.isEmpty() && mergedUsage.uses.isEmpty()) {
			return
		}

		// 检查变量能否在某个赋值点声明
		if (checkDeclareAtAssign(usageList, mergedUsage)) {
			return
		}

		// 未找到合适区域，则在方法开头声明
		declareVarInRegion(checkNotNull(mth.region), codeVar)
	}

	/** 把同一源码变量的多个 SSA 变量归并到一个 CodeVar */
	private fun collectCodeVars(mth: MethodNode): List<CodeVar> {
		val codeVars: MutableMap<CodeVar, MutableList<SSAVar>> = LinkedHashMap()
		for (ssaVar in mth.SVars) {
			if (ssaVar.codeVar.isThis) {
				continue
			}
			val codeVar = ssaVar.codeVar
			val list = codeVars.computeIfAbsent(codeVar) { ArrayList() }
			list.add(ssaVar)
		}

		for ((codeVar, list) in codeVars) {
			for (ssaVar in list) {
				val localCodeVar = ssaVar.codeVar
				codeVar.mergeFlagsFrom(localCodeVar)
			}
			if (list.size > 1) {
				for (ssaVar in list) {
					ssaVar.setCodeVar(codeVar)
				}
			}
			codeVar.ssaVars = list
		}
		return ArrayList(codeVars.keys)
	}

	private fun mergeUsageMaps(codeVars: List<CodeVar>, ssaUsageMap: Map<SSAVar?, VarUsage>): Map<CodeVar, List<VarUsage>> {
		val codeVarUsage: MutableMap<CodeVar, List<VarUsage>> = LinkedHashMap(codeVars.size)
		for (codeVar in codeVars) {
			val list = ArrayList<VarUsage>()
			for (ssaVar in codeVar.ssaVars) {
				val usage = ssaUsageMap[ssaVar]
				if (usage != null) {
					list.add(usage)
				}
			}
			codeVarUsage[codeVar] = Utils.lockList(list)
		}
		return codeVarUsage
	}
	private fun checkDeclareAtAssign(list: List<VarUsage>, mergedUsage: VarUsage): Boolean {
		if (mergedUsage.assigns.isEmpty()) {
			return false
		}
		for (u in list) {
			for (assign in u.assigns) {
				if (canDeclareAt(mergedUsage, assign)) {
					return checkDeclareAtAssign(checkNotNull(u.getVar()))
				}
			}
		}
		return false
	}

	private fun canDeclareAt(usage: VarUsage, usePlace: UsePlace): Boolean {
		val region = usePlace.region
		// 处理变量在多个循环中使用的场景
		if (region is LoopRegion) {
			for (use in usage.assigns) {
				if (!RegionUtils.isRegionContainsRegion(region, use.region)) {
					return false
				}
			}
		}
		// 不能在 else-if 链的 else 与下一个 if 之间声明
		if (region.contains(AFlag.ELSE_IF_CHAIN)) {
			return false
		}
		return isAllUseAfter(usePlace, usage.assigns) &&
			isAllUseAfter(usePlace, usage.uses)
	}

	/** 检查是否所有 [usePlaces] 都在 [checkPlace] 之后 */
	private fun isAllUseAfter(checkPlace: UsePlace, usePlaces: List<UsePlace>): Boolean {
		val region = checkPlace.region
		val block = checkPlace.block
		val toCheck: MutableSet<UsePlace> = HashSet(usePlaces)
		var blockFound = false
		for (subBlock in region.subBlocks) {
			if (!blockFound && subBlock === block) {
				blockFound = true
			}
			if (blockFound) {
				toCheck.removeAll { isContainerContainsUsePlace(subBlock, it) }
				if (toCheck.isEmpty()) {
					return true
				}
			}
		}
		return false
	}

	private fun isContainerContainsUsePlace(subBlock: IContainer, usePlace: UsePlace): Boolean {
		if (subBlock === usePlace.block) {
			return true
		}
		if (subBlock is IRegion) {
			return RegionUtils.isRegionContainsRegion(subBlock, usePlace.region)
		}
		return false
	}

	/** 尝试在 SSA 变量的赋值指令处声明 */
	private fun checkDeclareAtAssign(ssaVar: SSAVar): Boolean {
		val arg = ssaVar.assign
		val parentInsn = arg.getParentInsn()
		if (parentInsn == null ||
			parentInsn.contains(AFlag.WRAPPED) ||
			parentInsn.type == InsnType.PHI
		) {
			return false
		}
		if (arg != parentInsn.result) {
			return false
		}
		parentInsn.add(AFlag.DECLARE_VAR)
		ssaVar.codeVar.isDeclared = true
		return true
	}

	private fun declareVarInRegion(region: IContainer, v: CodeVar) {
		if (v.isDeclared) {
			LOG.warn("Try to declare already declared variable: {}", v)
			return
		}
		var dv = region.get(AType.DECLARE_VARIABLES)
		if (dv == null) {
			dv = DeclareVariablesAttr()
			region.addAttr(dv)
		}
		dv.addVar(v)
		v.isDeclared = true
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ProcessVariables::class.java)
	}
}
