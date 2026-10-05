package kadx.core.dex.visitors.regions.variables

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.DeclareVariablesAttr
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.CodeVar
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.loops.LoopRegion
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.regions.AbstractRegionVisitor
import kadx.core.dex.visitors.regions.DepthRegionTraversal
import kadx.core.dex.visitors.typeinference.TypeCompareEnum
import kadx.core.utils.ListUtils
import kadx.core.utils.RegionUtils
import kadx.core.utils.Utils
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
		if (codeVarUsage.isEmpty()) {
			return
		}
		// 构建区域树 DFS 序号索引，供声明点检查做 O(1) 区间判断
		val regionIndex = RegionOrderIndex(checkNotNull(mth.region))
		for ((codeVar, usageList) in codeVarUsage) {
			declareVar(mth, codeVar, usageList, regionIndex)
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
								if (ssaVar == null) {
									// 上游同路径会 NPE；此处某些寄存器结果尚未绑定 SSA 变量，跳过变量清理即可
									LOG.debug("Unused result without SSA var in {}: {}", mth, insn)
								} else {
									mth.removeSVar(ssaVar)
									for (arg in ssaVar.useList) {
										arg.resetSSAVar()
									}
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
							// 跨类解析的方法可能处于未加载状态（GENERATED_AND_UNLOADED），
							// argRegs 不可用——跳过本优化即可，方法照常反编译
							val mthArgs: List<RegisterArg>? = try {
								resolveMth.argRegs
							} catch (e: Exception) {
								null
							}
							if (mthArgs != null) {
								val insnPos = parentInsn.getArgIndex(arg)
								if (0 <= insnPos && insnPos < mthArgs.size) {
									val mthArg = mthArgs[insnPos]
									if (mthArg.contains(AFlag.REMOVE) && arg.sameType(mthArg)) {
										arg.add(AFlag.DONT_GENERATE)
										return true
									}
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
	private fun declareVar(mth: MethodNode, codeVar: CodeVar, usageList: List<VarUsage>, regionIndex: RegionOrderIndex) {
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
		if (checkDeclareAtAssign(usageList, mergedUsage, regionIndex)) {
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
	private fun checkDeclareAtAssign(list: List<VarUsage>, mergedUsage: VarUsage, regionIndex: RegionOrderIndex): Boolean {
		if (mergedUsage.assigns.isEmpty()) {
			return false
		}
		val assignsBounds = regionIndex.boundsOf(mergedUsage.assigns)
		val usesBounds = regionIndex.boundsOf(mergedUsage.uses)
		for (u in list) {
			for (assign in u.assigns) {
				if (canDeclareAt(mergedUsage, assign, assignsBounds, usesBounds, regionIndex)) {
					return checkDeclareAtAssign(checkNotNull(u.getVar()))
				}
			}
		}
		return false
	}

	private fun canDeclareAt(
		usage: VarUsage,
		usePlace: UsePlace,
		assignsBounds: RegionOrderIndex.Bounds,
		usesBounds: RegionOrderIndex.Bounds,
		regionIndex: RegionOrderIndex,
	): Boolean {
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
		return regionIndex.isAllUseAfter(usePlace, assignsBounds) &&
			regionIndex.isAllUseAfter(usePlace, usesBounds)
	}

	/**
	 * 方法区域树的 DFS 前序编号索引：
	 * - [blockOrder]：每个块的前序 DFS 编号；
	 * - [regionIntervals]：每个区域所覆盖块编号的连续区间（区域树嵌套无重叠，故必为连续区间）。
	 *
	 * 用于把「所有使用位置都在 [checkPlace] 之后（含同块）且包含于其区域」的判断
	 * 从 HashSet + 逐层向上遍历（对超大方法退化为平方级，卡死反编译）降为 O(1) 区间比较：
	 * 使用位置全部满足 `区间包含 && 编号不小于检查块` ⟺ `min ≥ max(区间首, 检查块) && max ≤ 区间尾`。
	 */
	private class RegionOrderIndex(root: IRegion) {
		private val blockOrder: MutableMap<IBlock, Int> = HashMap()
		private val regionIntervals: MutableMap<IRegion, IntRange> = HashMap()

		init {
			walk(root)
		}

		private fun walk(container: IContainer): IntRange {
			if (container is IBlock) {
				val idx = blockOrder.size
				blockOrder[container] = idx
				return idx..idx
			}
			val region = container as IRegion
			var min = Int.MAX_VALUE
			var max = Int.MIN_VALUE
			for (sub in region.subBlocks) {
				val range = walk(sub)
				if (range.isEmpty()) {
					continue
				}
				if (range.first < min) {
					min = range.first
				}
				if (range.last > max) {
					max = range.last
				}
			}
			val interval = if (min > max) IntRange.EMPTY else min..max
			regionIntervals[region] = interval
			return interval
		}

		/** 计算 [usePlaces] 的 DFS 编号范围；遇到索引外的块返回“不可证明”界（必定失败）；空列表返回“无约束”界 */
		fun boundsOf(usePlaces: List<UsePlace>): Bounds {
			var min = Int.MAX_VALUE
			var max = Int.MIN_VALUE
			for (place in usePlaces) {
				val order = blockOrder[place.block]
				if (order == null) {
					return Bounds(Int.MIN_VALUE, Int.MAX_VALUE)
				}
				if (order < min) {
					min = order
				}
				if (order > max) {
					max = order
				}
			}
			return Bounds(min, max)
		}

		/** 检查 [bounds] 覆盖的所有使用位置是否都在 [checkPlace] 之后（含同块）且包含于其所在区域 */
		fun isAllUseAfter(checkPlace: UsePlace, bounds: Bounds): Boolean {
			val interval = regionIntervals[checkPlace.region] ?: return false
			val blockPos = blockOrder[checkPlace.block] ?: return false
			return bounds.minOrder >= interval.first &&
				bounds.minOrder >= blockPos &&
				bounds.maxOrder <= interval.last
		}

		/** DFS 编号范围 [minOrder, maxOrder] */
		class Bounds(val minOrder: Int, val maxOrder: Int)
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
