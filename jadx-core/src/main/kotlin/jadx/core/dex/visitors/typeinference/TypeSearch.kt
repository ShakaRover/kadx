package jadx.core.dex.visitors.typeinference

import jadx.core.Consts
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet

/**
 * 慢速但更强的“多变量联合类型搜索”算法。
 *
 * **何时使用**：快速类型传播失败后，对少数无法确定的变量做穷举搜索。
 *
 * **算法阶段**：
 * 1. 为每个变量收集所有可能的候选类型（[fillTypeCandidates]）；
 * 2. 为每个变量构建动态约束（[collectConstraints]，处理 MOVE / PHI）；
 * 3. 先独立求解“无依赖”的变量（[resolveIndependentVariables]）；
 * 4. 剩余变量按“多位计数器”方式穷举组合（[search]），直到所有约束满足；
 * 5. 把结果应用回 SSA 变量（[applyResolvedVars]）。
 *
 * **Kotlin 转换说明**：
 * - 原局部变量名 `var`（关键字）统一重命名为 `varInfo`；
 * - 流式 `flatMap/allMatch/filter` 在热点路径改写为普通 `for` 循环；
 * - `ssaVar.getTypeInfo()` 等合成属性改为真实属性 `ssaVar.typeInfo`。
 */
class TypeSearch(private val mth: MethodNode) {

	private val state = TypeSearchState(mth)
	private val typeUpdate = mth.root().typeUpdate
	private val typeCompare = typeUpdate.typeCompare

	fun run(): Boolean {
		if (mth.SVars.size > VARS_PROCESS_LIMIT) {
			mth.addWarnComment(
				"Multi-variable search skipped. Vars limit reached: " + mth.SVars.size +
					" (expected less than " + VARS_PROCESS_LIMIT + ")",
			)
			return false
		}
		for (ssaVar in mth.SVars) {
			fillTypeCandidates(ssaVar)
		}
		for (ssaVar in mth.SVars) {
			collectConstraints(ssaVar)
		}

		// 先快速求解“没有依赖”的变量
		for (varInfo in state.unresolvedVars) {
			resolveIndependentVariables(varInfo)
		}

		val searchSuccess: Boolean
		val vars = state.unresolvedVars
		if (vars.isEmpty()) {
			searchSuccess = true
		} else {
			searchSuccess = search(vars) && fullCheck(vars)
			if (Consts.DEBUG_TYPE_INFERENCE && !searchSuccess) {
				LOG.debug("Multi-variable search failed")
			}
		}
		if (searchSuccess) {
			return applyResolvedVars()
		}
		return false
	}

	/** 把搜索得到的确定类型写回 SSA 变量，并触发一次类型更新。 */
	private fun applyResolvedVars(): Boolean {
		val resolvedVars = state.resolvedVars
		val updatedVars = ArrayList<TypeSearchVarInfo>()
		for (varInfo in resolvedVars) {
			val ssaVar = varInfo.getVar()
			val resolvedType = varInfo.getCurrentType()
			if (!resolvedType.isTypeKnown()) {
				// 忽略仍未知的变量
				continue
			}
			if (resolvedType == ssaVar.typeInfo.getType()) {
				// 类型已经设置过
				continue
			}
			ssaVar.setType(resolvedType)
			updatedVars.add(varInfo)
		}
		var applySuccess = true
		for (varInfo in updatedVars) {
			val res = typeUpdate.applyWithWiderIgnSame(mth, varInfo.getVar(), varInfo.getCurrentType())
			if (res == TypeUpdateResult.REJECT) {
				mth.addDebugComment("Multi-variable search result rejected for $varInfo")
				applySuccess = false
			}
		}
		return applySuccess
	}

	/**
	 * 穷举所有候选类型组合，直到 [fullCheck] 通过。
	 * 类似多位计数器：最低位变量先自增，回绕时向高位进位。
	 */
	private fun search(vars: List<TypeSearchVarInfo>): Boolean {
		val len = vars.size
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.debug("Run multi-variable search for {} vars: ", len)
			val sb = StringBuilder()
			var count = 1L
			for (varInfo in vars) {
				LOG.debug("  {}", varInfo)
				val size = varInfo.getCandidateTypes().size
				sb.append(" * ").append(size)
				count *= size
			}
			sb.append(" = ").append(count)
			LOG.debug(" max iterations count = {}", sb)
		}

		// 准备变量：重置到第一个候选
		for (varInfo in vars) {
			varInfo.reset()
		}
		// 检查所有类型组合
		var n = 0
		val i = 0
		while (!fullCheck(vars)) {
			val first = vars[i]
			if (first.nextType()) {
				var k = i + 1
				if (k >= len) {
					return false
				}
				var next = vars[k]
				while (true) {
					if (next.nextType()) {
						k++
						if (k >= len) {
							return false
						}
						next = vars[k]
					} else {
						break
					}
				}
			}
			n++
			if (n > SEARCH_ITERATION_LIMIT) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug(" > iterations limit reached: {}", SEARCH_ITERATION_LIMIT)
				}
				return false
			}
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.debug(" > done after {} iterations", n)
		}
		// 标记所有变量为已确定
		for (varInfo in vars) {
			varInfo.setTypeResolved(true)
		}
		return true
	}

	/** 若变量的所有相关变量都已确定，则单独搜索该变量。 */
	private fun resolveIndependentVariables(varInfo: TypeSearchVarInfo): Boolean {
		var allRelatedVarsResolved = true
		for (constraint in varInfo.getConstraints()) {
			for (v in constraint.getRelatedVars()) {
				if (!state.getVarInfo(v).isTypeResolved()) {
					allRelatedVarsResolved = false
					break
				}
			}
			if (!allRelatedVarsResolved) {
				break
			}
		}
		if (!allRelatedVarsResolved) {
			return false
		}
		// 变量独立，直接单变量搜索
		varInfo.reset()
		do {
			if (singleCheck(varInfo)) {
				varInfo.setTypeResolved(true)
				return true
			}
		} while (!varInfo.nextType())

		return false
	}

	private fun fullCheck(vars: List<TypeSearchVarInfo>): Boolean {
		for (varInfo in vars) {
			if (!singleCheck(varInfo)) {
				return false
			}
		}
		return true
	}

	private fun singleCheck(varInfo: TypeSearchVarInfo): Boolean {
		if (varInfo.isTypeResolved()) {
			return true
		}
		for (constraint in varInfo.getConstraints()) {
			if (!constraint.check(state)) {
				return false
			}
		}
		return true
	}

	/** 为变量收集候选类型：来自赋值/使用边界、继承的宽/窄类型、以及 APUT 用法。 */
	private fun fillTypeCandidates(ssaVar: SSAVar) {
		val varInfo = state.getVarInfo(ssaVar)
		val immutableType = ssaVar.immutableType
		if (immutableType != null) {
			varInfo.markResolved(immutableType)
			return
		}
		val currentType = ssaVar.typeInfo.getType()
		if (currentType.isTypeKnown()) {
			varInfo.markResolved(currentType)
			return
		}

		val assigns = LinkedHashSet<ArgType>()
		val uses = LinkedHashSet<ArgType>()
		val bounds = ssaVar.typeInfo.bounds
		for (bound in bounds) {
			if (bound.getBound() == BoundEnum.ASSIGN) {
				assigns.add(bound.getType())
			} else {
				uses.add(bound.getType())
			}
		}

		val candidateTypes = LinkedHashSet<ArgType>()
		addCandidateTypes(bounds, candidateTypes, assigns)
		addCandidateTypes(bounds, candidateTypes, uses)

		for (assignType in assigns) {
			addCandidateTypes(bounds, candidateTypes, getWiderTypes(assignType))
		}
		for (useType in uses) {
			addCandidateTypes(bounds, candidateTypes, getNarrowTypes(useType))
		}

		addUsageTypeCandidates(ssaVar, bounds, candidateTypes)

		val size = candidateTypes.size
		if (size == 0) {
			varInfo.setTypeResolved(true)
			varInfo.setCurrentType(ArgType.UNKNOWN)
			varInfo.setCandidateTypes(Collections.emptyList())
		} else if (size == 1) {
			varInfo.setTypeResolved(true)
			varInfo.setCurrentType(candidateTypes.iterator().next())
			varInfo.setCandidateTypes(Collections.emptyList())
		} else {
			varInfo.setTypeResolved(false)
			varInfo.setCurrentType(ArgType.UNKNOWN)
			val types = ArrayList(candidateTypes)
			types.sortWith(typeCompare.reversedComparator)
			varInfo.setCandidateTypes(Collections.unmodifiableList(types))
		}
	}

	/** 处理 `arr[i] = v` 场景：变量若用于 APUT 的数组参数，可推断为数组类型。 */
	private fun addUsageTypeCandidates(ssaVar: SSAVar, bounds: Set<ITypeBound>, candidateTypes: MutableSet<ArgType>) {
		for (useArg in ssaVar.useList) {
			val parentInsn = useArg.getParentInsn()
			if (parentInsn != null) {
				val insnType = parentInsn.type
				if (insnType == InsnType.APUT) {
					val aputType = parentInsn.getArg(2).getType()
					if (aputType.isTypeKnown()) {
						addCandidateType(bounds, candidateTypes, ArgType.array(aputType))
					}
				}
			}
		}
	}

	private fun addCandidateTypes(bounds: Set<ITypeBound>, collectedTypes: MutableSet<ArgType>, candidateTypes: Collection<ArgType>) {
		for (candidateType in candidateTypes) {
			if (addCandidateType(bounds, collectedTypes, candidateType)) {
				return
			}
		}
	}

	private fun addCandidateType(bounds: Set<ITypeBound>, collectedTypes: MutableSet<ArgType>, candidateType: ArgType): Boolean {
		if (candidateType.isTypeKnown() && typeUpdate.inBounds(bounds, candidateType)) {
			collectedTypes.add(candidateType)
			if (collectedTypes.size > CANDIDATES_COUNT_LIMIT) {
				return true
			}
		}
		return false
	}

	/** 取更宽的候选：对象类型的所有父类型。 */
	private fun getWiderTypes(type: ArgType): List<ArgType> {
		if (type.isTypeKnown()) {
			if (type.isObject()) {
				val ancestors = checkNotNull(mth.root().getClsp()).getSuperTypes(type.getObject())
				val list = ArrayList<ArgType>(ancestors.size)
				for (ancestor in ancestors) {
					list.add(ArgType.`object`(ancestor))
				}
				return list
			}
		} else {
			return expandUnknownType(type)
		}
		return Collections.emptyList()
	}

	/** 取更窄的候选：对象类型的所有实现类。 */
	private fun getNarrowTypes(type: ArgType): List<ArgType> {
		if (type.isTypeKnown()) {
			if (type.isObject()) {
				if (type == ArgType.OBJECT) {
					// Object 的实现类太多，只返回自身
					return Collections.singletonList(ArgType.OBJECT)
				}
				val impList = checkNotNull(mth.root().getClsp()).getImplementations(type.getObject())
				val list = ArrayList<ArgType>(impList.size)
				for (imp in impList) {
					list.add(ArgType.`object`(imp))
				}
				return list
			}
		} else {
			return expandUnknownType(type)
		}
		return Collections.emptyList()
	}

	/** 把“可能类型集合”展开为具体候选类型。 */
	private fun expandUnknownType(type: ArgType): List<ArgType> {
		val list = ArrayList<ArgType>()
		for (possibleType in type.getPossibleTypes()) {
			list.add(ArgType.convertFromPrimitiveType(possibleType))
		}
		return list
	}

	/** 为变量收集约束（MOVE / PHI）。 */
	private fun collectConstraints(ssaVar: SSAVar) {
		val varInfo = state.getVarInfo(ssaVar)
		if (varInfo.isTypeResolved()) {
			varInfo.setConstraints(Collections.emptyList())
			return
		}
		val constraints = ArrayList<ITypeConstraint>()
		addConstraint(constraints, makeConstraint(ssaVar.assign))
		for (regArg in ssaVar.useList) {
			addConstraint(constraints, makeConstraint(regArg))
		}
		varInfo.setConstraints(constraints)
	}

	private fun addConstraint(constraints: MutableList<ITypeConstraint>, constraint: ITypeConstraint?) {
		if (constraint != null) {
			constraints.add(constraint)
		}
	}

	private fun makeConstraint(arg: RegisterArg): ITypeConstraint? {
		val insn = arg.getParentInsn() ?: return null
		if (arg.isTypeImmutable()) {
			return null
		}
		return when (insn.type) {
			InsnType.MOVE -> makeMoveConstraint(insn, arg)
			InsnType.PHI -> makePhiConstraint(insn, arg)
			else -> null
		}
	}

	/** MOVE 约束：结果类型必须等于（或宽于）源参数类型。 */
	private fun makeMoveConstraint(insn: InsnNode, arg: RegisterArg): ITypeConstraint? {
		if (!insn.getArg(0).isRegister) {
			return null
		}
		return object : AbstractTypeConstraint(insn, arg) {
			override fun check(state: TypeSearchState): Boolean {
				val resType = state.getArgType(checkNotNull(insn.getResult()))
				val argType = state.getArgType(insn.getArg(0))
				val res = typeCompare.compareTypes(resType, argType)
				return res.isEqual() || res.isWider()
			}
		}
	}

	/** PHI 约束：结果类型必须与所有参数类型相同。 */
	private fun makePhiConstraint(insn: InsnNode, arg: RegisterArg): ITypeConstraint {
		return object : AbstractTypeConstraint(insn, arg) {
			override fun check(state: TypeSearchState): Boolean {
				val resType = state.getArgType(checkNotNull(insn.getResult()))
				for (insnArg in insn.getArguments()) {
					val argType = state.getArgType(insnArg)
					if (argType != resType) {
						return false
					}
				}
				return true
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TypeSearch::class.java)

		private const val VARS_PROCESS_LIMIT = 5_000
		private const val CANDIDATES_COUNT_LIMIT = 10
		private const val SEARCH_ITERATION_LIMIT = 1_000_000
	}
}
