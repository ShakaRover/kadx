package jadx.core.dex.visitors.typeinference

import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.AnonymousClassAttr
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InvokeType
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.nodes.utils.MethodUtils
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.AttachMethodDetails
import jadx.core.dex.visitors.ConstInlineVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.ssa.SSATransform
import jadx.core.utils.exceptions.JadxOverflowException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 类型推导主 Pass：为每个 SSA 变量计算“最合适的类型”。
 *
 * **算法流程**（[visit] 中依次执行）：
 * 1. [assignImmutableTypes]：先把带 `IMMUTABLE_TYPE` 标记的变量固定下来；
 * 2. [initTypeBounds]：从赋值与使用处收集初始类型边界（bound）；
 * 3. [runTypePropagation]：先设置不可变类型，再从边界中选出最优类型并传播。
 *
 * **Kotlin 转换说明**：
 * - Java 多 catch `StackOverflowError | BootstrapMethodError` 拆为两个 `catch`；
 * - `ssaVar.getTypeInfo()` 等合成属性改为真实属性 `ssaVar.typeInfo`；
 * - 流式 `selectBestTypeFromBounds` 改写为普通 `for` 循环（热点路径）。
 */
@JadxVisitor(
	name = "Type Inference",
	desc = "Calculate best types for SSA variables",
	runAfter = [SSATransform::class, ConstInlineVisitor::class, AttachMethodDetails::class],
)
class TypeInferenceVisitor : AbstractVisitor() {

	private lateinit var root: RootNode
	private lateinit var typeUpdate: TypeUpdate

	override fun init(root: RootNode) {
		this.root = root
		this.typeUpdate = root.typeUpdate
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.info("Start type inference in method: {}", mth)
		}
		try {
			assignImmutableTypes(mth)
			initTypeBounds(mth)
			runTypePropagation(mth)
		} catch (e: StackOverflowError) {
			mth.addError("Type inference failed with stack overflow", JadxOverflowException(e.message))
		} catch (e: BootstrapMethodError) {
			mth.addError("Type inference failed with stack overflow", JadxOverflowException(e.message))
		} catch (e: Exception) {
			mth.addError("Type inference failed", e)
		}
	}

	/**
	 * 从赋值与使用处收集初始类型边界。
	 */
	fun initTypeBounds(mth: MethodNode) {
		val ssaVars = mth.SVars
		for (ssaVar in ssaVars) {
			attachBounds(ssaVar)
		}
		for (ssaVar in ssaVars) {
			mergePhiBounds(ssaVar)
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			for (ssaVar in ssaVars.sorted()) {
				LOG.debug("Type bounds for {}: {}", ssaVar.toShortString(), ssaVar.typeInfo.bounds)
			}
		}
	}

	/**
	 * 从使用处猜测类型，并尝试设置到当前变量及所有关联指令。
	 */
	fun runTypePropagation(mth: MethodNode): Boolean {
		val ssaVars = mth.SVars
		for (ssaVar in ssaVars) {
			setImmutableType(mth, ssaVar)
		}
		for (ssaVar in ssaVars) {
			setBestType(mth, ssaVar)
		}
		return true
	}

	private fun setImmutableType(mth: MethodNode, ssaVar: SSAVar) {
		try {
			val immutableType = ssaVar.immutableType
			if (immutableType != null) {
				val result = typeUpdate.applyWithWiderIgnSame(mth, ssaVar, immutableType)
				if (Consts.DEBUG_TYPE_INFERENCE && result == TypeUpdateResult.REJECT) {
					LOG.info("Reject initial immutable type {} for {}", immutableType, ssaVar)
				}
			}
		} catch (e: JadxOverflowException) {
			throw e
		} catch (e: Exception) {
			mth.addWarnComment("Failed to set immutable type for var: $ssaVar", e)
		}
	}

	private fun setBestType(mth: MethodNode, ssaVar: SSAVar) {
		try {
			calculateFromBounds(mth, ssaVar)
		} catch (e: JadxOverflowException) {
			throw e
		} catch (e: Exception) {
			mth.addWarnComment("Failed to calculate best type for var: $ssaVar", e)
		}
	}

	private fun calculateFromBounds(mth: MethodNode, ssaVar: SSAVar) {
		val typeInfo = ssaVar.typeInfo
		val bounds = typeInfo.bounds
		val bestTypeOpt = selectBestTypeFromBounds(bounds)
		if (bestTypeOpt == null) {
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.warn("Failed to select best type from bounds, count={} : ", bounds.size)
				for (bound in bounds) {
					LOG.warn("  {}", bound)
				}
			}
			return
		}
		val candidateType = bestTypeOpt
		val result = typeUpdate.apply(mth, ssaVar, candidateType)
		if (Consts.DEBUG_TYPE_INFERENCE && result == TypeUpdateResult.REJECT) {
			if (ssaVar.typeInfo.getType() == candidateType) {
				LOG.info("Same type rejected: {} -> {}, bounds: {}", ssaVar, candidateType, bounds)
			} else if (candidateType.isTypeKnown()) {
				LOG.debug("Type rejected: {} -> {}, bounds: {}", ssaVar, candidateType, bounds)
			}
		}
	}

	/** 从所有边界中选出“最宽”的类型（比较器取最大值）。 */
	private fun selectBestTypeFromBounds(bounds: Set<ITypeBound>): ArgType? {
		val comparator = typeUpdate.typeCompare.comparator
		var best: ArgType? = null
		for (bound in bounds) {
			val type = bound.type
			if (best == null || comparator.compare(type, best) > 0) {
				best = type
			}
		}
		return best
	}

	private fun attachBounds(ssaVar: SSAVar) {
		val typeInfo = ssaVar.typeInfo
		typeInfo.bounds.clear()
		val assign = ssaVar.assign
		addAssignBound(typeInfo, assign)

		for (regArg in ssaVar.useList) {
			addBound(typeInfo, makeUseBound(regArg))
		}
	}

	/** 把 PHI 指令各变量的边界合并到结果变量上。 */
	private fun mergePhiBounds(ssaVar: SSAVar) {
		for (usedInPhi in ssaVar.usedInPhi) {
			val bounds = ssaVar.typeInfo.bounds
			bounds.addAll(checkNotNull(usedInPhi.result?.sVar).typeInfo.bounds)
			for (arg in usedInPhi.getArguments()) {
				bounds.addAll(checkNotNull((arg as RegisterArg).sVar).typeInfo.bounds)
			}
		}
	}

	private fun addBound(typeInfo: TypeInfo, bound: ITypeBound?) {
		if (bound == null) {
			return
		}
		if (bound is ITypeBoundDynamic || bound.type !== ArgType.UNKNOWN) {
			typeInfo.bounds.add(bound)
		}
	}

	private fun addAssignBound(typeInfo: TypeInfo, assign: RegisterArg) {
		val immutableType = assign.immutableType
		if (immutableType != null) {
			addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, immutableType))
			return
		}
		val insn = assign.getParentInsn()
		val result = insn?.result
		if (insn == null || result == null) {
			addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, assign.getInitType()))
			return
		}
		when (insn.type) {
			InsnType.NEW_INSTANCE -> {
				val clsType = (insn as IndexInsnNode).index as ArgType
				addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, clsType))
			}

			InsnType.CONSTRUCTOR -> {
				val ctrClsType = replaceAnonymousType(insn as ConstructorInsn)
				addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, ctrClsType))
			}

			InsnType.CONST -> {
				val constLit = insn.getArg(0) as LiteralArg
				addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, constLit.getType()))
			}

			InsnType.MOVE_EXCEPTION -> {
				val excHandlerAttr: ExcHandlerAttr? = insn.get(AType.EXC_HANDLER)
				if (excHandlerAttr != null) {
					for (catchType in excHandlerAttr.handler.catchTypes) {
						addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, catchType.type))
					}
				} else {
					addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, result.getInitType()))
				}
			}

			InsnType.INVOKE -> {
				addBound(typeInfo, makeAssignInvokeBound(insn as InvokeNode))
			}

			InsnType.IGET -> {
				addBound(typeInfo, makeAssignFieldGetBound(insn as IndexInsnNode))
			}

			InsnType.CHECK_CAST -> {
				if (!insn.contains(AFlag.SOFT_CAST)) {
					addBound(typeInfo, TypeBoundCheckCastAssign(root, insn as IndexInsnNode))
				}
				// 软转换：忽略边界，更新时会再检查
			}

			else -> {
				addBound(typeInfo, TypeBoundConst(BoundEnum.ASSIGN, result.getInitType()))
			}
		}
	}

	/** 匿名类构造调用应使用其“基类型”，便于后续内联还原。 */
	private fun replaceAnonymousType(ctr: ConstructorInsn): ArgType {
		if (ctr.isNewInstance) {
			val ctrCls = root.resolveClass(ctr.classType)
			if (ctrCls != null && ctrCls.contains(AFlag.DONT_GENERATE)) {
				val baseTypeAttr = ctrCls.get(AType.ANONYMOUS_CLASS)
				if (baseTypeAttr != null && baseTypeAttr.inlineType == AnonymousClassAttr.InlineType.CONSTRUCTOR) {
					return baseTypeAttr.baseType
				}
			}
		}
		return ctr.classType.type
	}

	private fun makeAssignFieldGetBound(insn: IndexInsnNode): ITypeBound {
		val initType = checkNotNull(insn.result).getInitType()
		if (initType.containsTypeVariable()) {
			return TypeBoundFieldGetAssign(root, insn, initType)
		}
		return TypeBoundConst(BoundEnum.ASSIGN, initType)
	}

	private fun makeAssignInvokeBound(invokeNode: InvokeNode): ITypeBound {
		var boundType = invokeNode.callMth.returnType
		val genericReturnType = root.getMethodUtils().getMethodGenericReturnType(invokeNode)
		if (genericReturnType != null) {
			if (genericReturnType.containsTypeVariable()) {
				val invokeType = invokeNode.invokeType
				if (invokeNode.argsCount != 0 && invokeType != InvokeType.STATIC && invokeType != InvokeType.SUPER) {
					return TypeBoundInvokeAssign(root, invokeNode, genericReturnType)
				}
			} else {
				boundType = genericReturnType
			}
		}
		return TypeBoundConst(BoundEnum.ASSIGN, boundType)
	}

	private fun makeUseBound(regArg: RegisterArg): ITypeBound? {
		val insn = regArg.getParentInsn() ?: return null
		if (insn is BaseInvokeNode) {
			val invokeUseBound = makeInvokeUseBound(regArg, insn)
			if (invokeUseBound != null) {
				return invokeUseBound
			}
		}
		if (insn.type == InsnType.CHECK_CAST && insn.contains(AFlag.SOFT_CAST)) {
			// 忽略软转换
			return null
		}
		return TypeBoundConst(BoundEnum.USE, regArg.getInitType(), regArg)
	}

	private fun makeInvokeUseBound(regArg: RegisterArg, invoke: BaseInvokeNode): ITypeBound? {
		val instanceArg = invoke.getInstanceArg() ?: return null
		val methodUtils = root.getMethodUtils()
		val methodDetails = methodUtils.getMethodDetails(invoke) ?: return null
		if (instanceArg !== regArg) {
			val argIndex = invoke.getArgIndex(regArg) - invoke.getFirstArgOffset()
			val argType = methodDetails.argTypes[argIndex]
			if (!argType.containsTypeVariable()) {
				return null
			}
			return TypeBoundInvokeUse(root, invoke, regArg, argType)
		}

		// 覆写方法使用原始声明类作为类型
		if (methodDetails is MethodNode) {
			val declCls = methodUtils.getMethodOriginDeclClass(methodDetails)
			return TypeBoundConst(BoundEnum.USE, declCls.type, regArg)
		}
		return null
	}

	private fun assignImmutableTypes(mth: MethodNode) {
		for (ssaVar in mth.SVars) {
			val immutableType = getSsaImmutableType(ssaVar)
			if (immutableType != null) {
				ssaVar.markAsImmutable(immutableType)
			}
		}
	}

	override fun getName(): String = "TypeInferenceVisitor"

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TypeInferenceVisitor::class.java)

		private fun getSsaImmutableType(ssaVar: SSAVar): ArgType? {
			if (ssaVar.assign.contains(AFlag.IMMUTABLE_TYPE)) {
				return ssaVar.assign.getInitType()
			}
			for (reg in ssaVar.useList) {
				if (reg.contains(AFlag.IMMUTABLE_TYPE)) {
					return reg.getInitType()
				}
			}
			return null
		}
	}
}
