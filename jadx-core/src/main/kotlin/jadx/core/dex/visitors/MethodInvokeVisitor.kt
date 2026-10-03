package jadx.core.dex.visitors

import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.nodes.utils.TypeUtils
import jadx.core.dex.visitors.methods.MutableMethodDetails
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.dex.visitors.typeinference.TypeCompare
import jadx.core.dex.visitors.typeinference.TypeCompareEnum
import jadx.core.utils.InsnUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory
import java.util.ArrayList

/**
 * 处理方法调用（重载、可变参数）的附加信息。
 *
 * **做什么**：对每个 invoke 指令：
 * - 标记可变参数调用 [AFlag.VARARG_CALL]；
 * - 在存在重载方法时，推断并插入必要的 cast，使生成代码能正确匹配到目标重载；
 * - 解析泛型类型变量，使重载匹配更精确。
 *
 * **为什么在代码收缩与 ModVisitor 之后、SimplifyVisitor 之前**：需要在字符串拼接/去 cast 之前完成重载解析。
 */
@JadxVisitor(
	name = "MethodInvokeVisitor",
	desc = "Process additional info for method invocation (overload, vararg)",
	runAfter = [
		CodeShrinkVisitor::class,
		ModVisitor::class,
	],
	runBefore = [
		SimplifyVisitor::class, // 在 cast 移除与 StringBuilder 替换之前运行
	],
)
class MethodInvokeVisitor : AbstractVisitor() {

	private lateinit var root: RootNode

	override fun init(root: RootNode) {
		this.root = root
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (block in checkNotNull(mth.getBasicBlocks())) {
			if (block.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			for (insn in block.getInstructions()) {
				if (insn.contains(AFlag.DONT_GENERATE)) {
					continue
				}
				insn.visitInsns(
					{ `in` ->
						if (`in` is BaseInvokeNode) {
							processInvoke(mth, `in`)
						}
					},
				)
			}
		}
	}

	private fun processInvoke(parentMth: MethodNode, invokeInsn: BaseInvokeNode) {
		val callMth = invokeInsn.callMth
		if (callMth.argsCount == 0) {
			return
		}
		val mthDetails = root.getMethodUtils().getMethodDetails(invokeInsn)
		if (mthDetails == null) {
			if (Consts.DEBUG) {
				parentMth.addDebugComment("Method info not found: $callMth")
			}
			processUnknown(invokeInsn)
		} else {
			if (mthDetails.isVarArg()) {
				val last = Utils.last(mthDetails.getArgTypes())
				if (last != null && last.isArray()) {
					invokeInsn.add(AFlag.VARARG_CALL)
				}
			}
			processOverloaded(parentMth, invokeInsn, mthDetails)
		}
	}

	private fun processOverloaded(parentMth: MethodNode, invokeInsn: BaseInvokeNode, mthDetails: IMethodDetails) {
		val callMth = invokeInsn.callMth
		val callCls = getCallClassFromInvoke(parentMth, invokeInsn, callMth)
		val overloadMethods = root.getMethodUtils().collectOverloadedMethods(callCls, callMth)
		if (overloadMethods.isEmpty()) {
			// 没有重载
			return
		}

		// 解析泛型类型变量
		val typeVarsMapping = getTypeVarsMapping(invokeInsn)
		val effectiveMthDetails = resolveTypeVars(mthDetails, typeVarsMapping)
		val effectiveOverloadMethods = ArrayList<IMethodDetails>(overloadMethods.size + 1)
		for (overloadMethod in overloadMethods) {
			effectiveOverloadMethods.add(resolveTypeVars(overloadMethod, typeVarsMapping))
		}
		effectiveOverloadMethods.add(effectiveMthDetails)

		// 搜索用于解析重载的 cast 类型
		val argsOffset = invokeInsn.getFirstArgOffset()
		val compilerVarTypes = collectCompilerVarTypes(invokeInsn, argsOffset)
		val castTypes = searchCastTypes(parentMth, effectiveMthDetails, effectiveOverloadMethods, compilerVarTypes)
		val resultCastTypes = expandTypes(parentMth, effectiveMthDetails, castTypes)
		applyArgsCast(invokeInsn, argsOffset, compilerVarTypes, resultCastTypes)
	}

	/**
	 * 方法信息未找到 => 为 'null' 参数添加 cast
	 */
	private fun processUnknown(invokeInsn: BaseInvokeNode) {
		val argsOffset = invokeInsn.getFirstArgOffset()
		val compilerVarTypes = collectCompilerVarTypes(invokeInsn, argsOffset)
		val castTypes = ArrayList(compilerVarTypes)
		if (replaceUnknownTypes(castTypes, invokeInsn.callMth.argumentsTypes)) {
			applyArgsCast(invokeInsn, argsOffset, compilerVarTypes, castTypes)
		}
	}

	private fun getCallClassFromInvoke(parentMth: MethodNode, invokeInsn: BaseInvokeNode, callMth: MethodInfo): ArgType {
		if (invokeInsn is ConstructorInsn) {
			if (invokeInsn.isSuper) {
				return checkNotNull(parentMth.parentClass.superClass)
			}
		}
		val instanceArg = invokeInsn.getInstanceArg()
		if (instanceArg != null) {
			return instanceArg.getType()
		}
		// 静态调用
		return callMth.declClass.type
	}

	private fun getTypeVarsMapping(invokeInsn: BaseInvokeNode): Map<ArgType, ArgType> {
		val callMthInfo = invokeInsn.callMth
		val declClsType = callMthInfo.declClass.type
		val callClsType = getClsCallType(invokeInsn, declClsType)

		val typeUtils: TypeUtils = root.getTypeUtils()
		val clsTypeVars = typeUtils.getTypeVariablesMapping(callClsType)
		val mthTypeVars = typeUtils.getTypeVarMappingForInvoke(invokeInsn)
		return checkNotNull(Utils.mergeMaps(clsTypeVars, mthTypeVars))
	}

	private fun getClsCallType(invokeInsn: BaseInvokeNode, declClsType: ArgType): ArgType {
		val instanceArg = invokeInsn.getInstanceArg()
		if (instanceArg != null) {
			return instanceArg.getType()
		}
		if (invokeInsn.getType() == InsnType.CONSTRUCTOR && invokeInsn.getResult() != null) {
			return checkNotNull(invokeInsn.getResult()).getType()
		}
		return declClsType
	}

	private fun applyArgsCast(
		invokeInsn: BaseInvokeNode,
		argsOffset: Int,
		compilerVarTypes: List<ArgType>,
		castTypes: List<ArgType>,
	) {
		val argsCount = invokeInsn.getArgsCount()
		for (i in argsOffset until argsCount) {
			val arg = invokeInsn.getArg(i)
			val origPos = i - argsOffset
			val compilerType = compilerVarTypes[origPos]
			val castType = castTypes[origPos]
			if (castType != null) {
				if (castType != compilerType) {
					if (arg.isLiteral && compilerType.isPrimitive() && castType.isPrimitive()) {
						arg.setType(castType)
						arg.add(AFlag.EXPLICIT_PRIMITIVE_TYPE)
					} else if (InsnUtils.isWrapped(arg, InsnType.CHECK_CAST)) {
						val wrapInsn = (arg as InsnWrapArg).wrapInsn as IndexInsnNode
						wrapInsn.index = castType
					} else {
						if (Consts.DEBUG_TYPE_INFERENCE) {
							LOG.info("Insert cast for invoke insn arg: {}, insn: {}", arg, invokeInsn)
						}
						val castInsn = IndexInsnNode(InsnType.CAST, castType, 1)
						castInsn.addArg(arg)
						castInsn.add(AFlag.EXPLICIT_CAST)
						val wrapCast = InsnArg.wrapArg(castInsn)
						wrapCast.setType(castType)
						invokeInsn.setArg(i, wrapCast)
					}
				} else {
					// 保护已存在的 cast
					if (arg.isInsnWrap) {
						val wrapInsn = (arg as InsnWrapArg).wrapInsn
						if (wrapInsn.getType() == InsnType.CHECK_CAST) {
							wrapInsn.add(AFlag.EXPLICIT_CAST)
						}
					}
				}
			}
		}
	}

	private fun resolveTypeVars(mthDetails: IMethodDetails, typeVarsMapping: Map<ArgType, ArgType>): IMethodDetails {
		val argTypes = mthDetails.getArgTypes()
		val argsCount = argTypes.size
		var fixed = false
		val fixedArgTypes = ArrayList<ArgType>(argsCount)
		for (argNum in 0 until argsCount) {
			val argType = argTypes[argNum]
			if (argType == null) {
				throw JadxRuntimeException("Null arg type in $mthDetails at: $argNum in: $argTypes")
			}
			if (argType.containsTypeVariable()) {
				var resolvedType = root.getTypeUtils().replaceTypeVariablesUsingMap(argType, typeVarsMapping)
				if (resolvedType == null || resolvedType == argType) {
					// 类型变量已被编译器擦除
					resolvedType = mthDetails.getMethodInfo().argumentsTypes[argNum]
				}
				fixedArgTypes.add(resolvedType)
				fixed = true
			} else {
				fixedArgTypes.add(argType)
			}
		}
		var returnType = mthDetails.getReturnType()
		if (returnType.containsTypeVariable()) {
			val resolvedType = root.getTypeUtils().replaceTypeVariablesUsingMap(returnType, typeVarsMapping)
			if (resolvedType == null || resolvedType.containsTypeVariable()) {
				returnType = mthDetails.getMethodInfo().returnType
				fixed = true
			}
		}

		if (!fixed) {
			return mthDetails
		}
		val mutableMethodDetails = MutableMethodDetails(mthDetails)
		mutableMethodDetails.setArgTypes(fixedArgTypes)
		mutableMethodDetails.setRetType(returnType)
		return mutableMethodDetails
	}

	private fun searchCastTypes(
		parentMth: MethodNode,
		mthDetails: IMethodDetails,
		overloadedMethods: List<IMethodDetails>,
		compilerVarTypes: List<ArgType>,
	): List<ArgType> {
		// 先试编译器类型
		if (isOverloadResolved(mthDetails, overloadedMethods, compilerVarTypes)) {
			return compilerVarTypes
		}
		val argsCount = compilerVarTypes.size
		val castTypes = ArrayList(compilerVarTypes)

		// 替换未知类型
		var changed = replaceUnknownTypes(castTypes, mthDetails.getArgTypes())
		if (changed && isOverloadResolved(mthDetails, overloadedMethods, castTypes)) {
			return castTypes
		}

		// 替换泛型类型
		changed = false
		for (i in 0 until argsCount) {
			val castType = castTypes[i]
			val mthType = mthDetails.getArgTypes()[i]
			if (!castType.isGeneric() && mthType.isGeneric()) {
				castTypes[i] = mthType
				changed = true
			}
		}
		if (changed && isOverloadResolved(mthDetails, overloadedMethods, castTypes)) {
			return castTypes
		}

		// 只有一个参数 => cast 就能解析
		if (argsCount == 1) {
			return mthDetails.getArgTypes()
		}
		if (Consts.DEBUG_OVERLOADED_CASTS) {
			// TODO: 尝试最小化 cast 数量
			parentMth.addDebugComment(
				"Failed to find minimal casts for resolve overloaded methods, cast all args instead" +
					"\n method: " + mthDetails +
					"\n arg types: " + compilerVarTypes +
					"\n candidates:" +
					"\n  " + Utils.listToString(overloadedMethods, "\n  "),
			)
		}
		// 无法解析 -> 对所有参数 cast
		return mthDetails.getArgTypes()
	}

	private fun replaceUnknownTypes(castTypes: MutableList<ArgType>, mthArgTypes: List<ArgType>): Boolean {
		val argsCount = castTypes.size
		var changed = false
		for (i in 0 until argsCount) {
			val castType = castTypes[i]
			if (!castType.isTypeKnown()) {
				val mthType = mthArgTypes[i]
				castTypes[i] = mthType
				changed = true
			}
		}
		return changed
	}

	/**
	 * 尽可能使用带泛型的类型
	 */
	private fun expandTypes(parentMth: MethodNode, methodDetails: IMethodDetails, castTypes: List<ArgType>): List<ArgType> {
		val typeCompare: TypeCompare = parentMth.root().getTypeCompare()
		val mthArgTypes = methodDetails.getArgTypes()
		val argsCount = castTypes.size
		val list = ArrayList<ArgType>(argsCount)
		for (i in 0 until argsCount) {
			val mthType = mthArgTypes[i]
			val castType = castTypes[i]
			val result = typeCompare.compareTypes(mthType, castType)
			if (result == TypeCompareEnum.NARROW_BY_GENERIC) {
				list.add(mthType)
			} else {
				list.add(castType)
			}
		}
		return list
	}

	private fun isOverloadResolved(
		expectedMthDetails: IMethodDetails,
		overloadedMethods: List<IMethodDetails>,
		castTypes: List<ArgType>,
	): Boolean {
		if (overloadedMethods.isEmpty()) {
			return false
		}
		// TODO: 搜索最接近的方法，而不是过滤
		val strictMethods = filterApplicableMethods(overloadedMethods, castTypes) { isStrictTypes(it) }
		if (strictMethods.size == 1) {
			return strictMethods[0] == expectedMthDetails
		}
		val resolvedMethods = filterApplicableMethods(overloadedMethods, castTypes) { isTypeApplicable(it) }
		if (resolvedMethods.size == 1) {
			return resolvedMethods[0] == expectedMthDetails
		}
		return false
	}

	private fun isStrictTypes(result: TypeCompareEnum): Boolean = result.isEqual()

	private fun isTypeApplicable(result: TypeCompareEnum): Boolean = result.isNarrowOrEqual() || result == TypeCompareEnum.WIDER_BY_GENERIC

	private fun filterApplicableMethods(
		methods: List<IMethodDetails>,
		types: List<ArgType>,
		acceptFunction: (TypeCompareEnum) -> Boolean,
	): List<IMethodDetails> {
		val list = ArrayList<IMethodDetails>(methods.size)
		for (m in methods) {
			if (isMethodAcceptable(m, types, acceptFunction)) {
				list.add(m)
			}
		}
		return list
	}

	private fun isMethodAcceptable(
		methodDetails: IMethodDetails,
		types: List<ArgType>,
		acceptFunction: (TypeCompareEnum) -> Boolean,
	): Boolean {
		val mthTypes = methodDetails.getArgTypes()
		val argCount = mthTypes.size
		if (argCount != types.size) {
			return false
		}
		val typeCompare: TypeCompare = root.getTypeCompare()
		for (i in 0 until argCount) {
			val mthType = mthTypes[i]
			val argType = types[i]
			val result = typeCompare.compareTypes(argType, mthType)
			if (!acceptFunction(result)) {
				return false
			}
		}
		return true
	}

	private fun collectCompilerVarTypes(insn: BaseInvokeNode, argOffset: Int): List<ArgType> {
		val argsCount = insn.getArgsCount()
		val result = ArrayList<ArgType>(argsCount)
		for (i in argOffset until argsCount) {
			val arg = insn.getArg(i)
			result.add(getCompilerVarType(arg))
		}
		return result
	}

	/**
	 * 返回编译器看到的类型
	 */
	private fun getCompilerVarType(arg: InsnArg): ArgType {
		if (arg is LiteralArg) {
			val type = arg.getType()
			if (arg.literal == 0L) {
				if (type.isObject() || type.isArray()) {
					// null
					return ArgType.UNKNOWN_OBJECT
				}
			}
			if (type.isPrimitive() && !arg.contains(AFlag.EXPLICIT_PRIMITIVE_TYPE)) {
				return ArgType.INT
			}
			return arg.getType()
		}
		if (arg is RegisterArg) {
			return arg.getType()
		}
		if (arg is InsnWrapArg) {
			return getInsnCompilerType(arg, arg.wrapInsn)
		}
		throw JadxRuntimeException("Unknown var type for: $arg")
	}

	private fun getInsnCompilerType(arg: InsnArg, insn: InsnNode): ArgType = when (insn.getType()) {
		InsnType.CAST, InsnType.CHECK_CAST -> (insn as IndexInsnNode).getIndexAsType()

		else -> {
			if (insn.getResult() != null) {
				checkNotNull(insn.getResult()).getType()
			} else {
				arg.getType()
			}
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodInvokeVisitor::class.java)
	}
}
