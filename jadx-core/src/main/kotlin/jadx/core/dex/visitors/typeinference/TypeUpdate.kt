package jadx.core.dex.visitors.typeinference

import jadx.api.JadxArgs
import jadx.core.Consts
import jadx.core.clsp.ClspClass
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.ArithNode
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.nodes.utils.TypeUtils
import jadx.core.utils.exceptions.JadxOverflowException
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.EnumMap

/**
 * 类型更新的核心调度器：对一组相关 SSA 变量做类型检查与传播。
 *
 * **算法意图**：一次类型更新由“根参数 → 候选类型”发起，
 * 通过指令监听器（[listenerRegistry]）把类型变化扩散到同指令的其它参数，
 * 再顺着 SSA 变量的使用列表继续扩散。所有改动先记录到 [TypeUpdateInfo]，
 * 全部验证通过后才一次性写回（保证原子性），任一环节被拒绝则整体回滚。
 *
 * **Kotlin 转换说明**：
 * - 节点/参数引用比较严格使用 `===`（如 `insn.getResult() === arg`）；
 * - [typeCompare] 声明为属性，保留 `getTypeCompare()` JVM 方法；
 * - 监听器用 `fun interface` + 方法引用注册（Kotlin SAM 转换）；
 * - 局部变量 `var updateCallback` 重命名为 `val`。
 */
class TypeUpdate(private val root: RootNode) {

	private val listenerRegistry: Map<InsnType, ITypeListener> = initListenerRegistry()
	val typeCompare: TypeCompare = TypeCompare(root)
	private val args: JadxArgs = root.getArgs()

	/**
	 * 执行类型检查与传播。
	 */
	fun apply(mth: MethodNode, ssaVar: SSAVar, candidateType: ArgType): TypeUpdateResult = apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_EMPTY)

	/**
	 * 允许用更宽的类型覆盖（用于调试信息等特殊场景）。
	 */
	fun applyWithWiderAllow(mth: MethodNode, ssaVar: SSAVar, candidateType: ArgType): TypeUpdateResult = apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_WIDER)

	/**
	 * 强制设置类型（即使与当前类型相同也继续）。
	 */
	fun applyWithWiderIgnSame(mth: MethodNode, ssaVar: SSAVar, candidateType: ArgType): TypeUpdateResult = apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_WIDER_IGNORE_SAME)

	fun applyDebugInfo(mth: MethodNode, ssaVar: SSAVar, candidateType: ArgType): TypeUpdateResult = apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_APPLY_DEBUG)

	private fun apply(mth: MethodNode, ssaVar: SSAVar, candidateType: ArgType, flags: TypeUpdateFlags): TypeUpdateResult {
		try {
			if (!candidateType.isTypeKnown()) {
				return TypeUpdateResult.REJECT
			}
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.debug("Start type update for {} to {}", ssaVar.toShortString(), candidateType)
			}
			val updateInfo = TypeUpdateInfo(mth, flags, args)
			val result = queueTypeUpdate(updateInfo, ssaVar.assign, candidateType, null) ?: runUpdate(updateInfo)
			if (result == TypeUpdateResult.REJECT) {
				return TypeUpdateResult.REJECT
			}
			if (updateInfo.isEmpty()) {
				return TypeUpdateResult.SAME
			}
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.debug("Applying type {} to {}:", candidateType, ssaVar.toShortString())
				for (upd in updateInfo.sortedUpdates) {
					LOG.debug("  {} -> {} in {}", upd.type, upd.arg.toShortString(), upd.arg.getParentInsn())
				}
			}
			updateInfo.applyUpdates()
			return TypeUpdateResult.CHANGED
		} catch (e: JadxOverflowException) {
			throw e
		} catch (e: Exception) {
			throw JadxRuntimeException("Type update failed for variable: $ssaVar, new type: $candidateType", e)
		}
	}

	/** 逐个处理更新请求队列，并把结果通过回调链回传。 */
	private fun runUpdate(updateInfo: TypeUpdateInfo): TypeUpdateResult? {
		var result: TypeUpdateResult? = TypeUpdateResult.REJECT
		while (true) {
			val request = updateInfo.pollNextRequest() ?: return result
			val updateArg = request.arg
			val updateType = request.candidateType
			val newResult: TypeUpdateResult? = if (request.isDirect()) {
				requestUpdate(updateInfo, updateArg, updateType)
			} else {
				updateTypeForArg(updateInfo, updateArg, updateType)
			}
			updateInfo.saveCallback(request)
			if (newResult != null) {
				// 把结果沿回调链回传
				result = processCallbacks(updateInfo, newResult)
			}
		}
	}

	/**
	 * 排队一次参数类型更新。
	 *
	 * @param callback 结果计算出来后回调，可为 null（原样透传结果）
	 * @return null 表示已入队；非 null 表示验证失败、直接返回结果
	 */
	fun queueTypeUpdate(updateInfo: TypeUpdateInfo, arg: InsnArg, candidateType: ArgType, callback: ITypeUpdateCallback?): TypeUpdateResult? {
		// 验证本可以在队列处理时做，提前到这里是为了加速
		val res = verifyType(updateInfo, arg, candidateType)
		if (res != null) {
			if (callback == null) {
				return res
			}
			val result = callback.updateCallback(res)
			if (result == null) {
				updateInfo.saveCallback(TypeUpdateRequest(arg, candidateType, false, callback))
			}
			return result
		}
		updateInfo.queueRequest(TypeUpdateRequest(arg, candidateType, false, callback))
		return null
	}

	fun queueDirectTypeUpdate(updateInfo: TypeUpdateInfo, arg: InsnArg, candidateType: ArgType, callback: ITypeUpdateCallback?): TypeUpdateResult? {
		updateInfo.queueRequest(TypeUpdateRequest(arg, candidateType, true, callback))
		return null
	}

	private fun updateTypeForArg(updateInfo: TypeUpdateInfo, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.debug("-> update type for: {} to {}", arg, candidateType)
		}
		if (arg is RegisterArg) {
			return updateTypeForSsaVar(updateInfo, checkNotNull(arg.sVar), candidateType)
		}
		return requestUpdate(updateInfo, arg, candidateType)
	}

	/** 验证候选类型是否可以被接受；返回 null 表示可以继续处理。 */
	private fun verifyType(updateInfo: TypeUpdateInfo, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		if (updateInfo.isProcessed(arg)) {
			return TypeUpdateResult.CHANGED
		}
		val currentType = arg.getType()
		val typeUpdateFlags = updateInfo.flags
		if (currentType == candidateType) {
			if (!typeUpdateFlags.isIgnoreSame()) {
				return TypeUpdateResult.SAME
			}
		} else {
			if (candidateType.isWildcard()) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Wildcard type rejected for {}: candidate={}, current={}", arg, candidateType, currentType)
				}
				return TypeUpdateResult.REJECT
			}

			val compareResult = typeCompare.compareTypes(candidateType, currentType)
			if (compareResult.isConflict()) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Type rejected for {}: candidate={} in conflict with current={}", arg, candidateType, currentType)
				}
				return TypeUpdateResult.REJECT
			}
			if (compareResult == TypeCompareEnum.UNKNOWN && typeUpdateFlags.isIgnoreUnknown()) {
				return TypeUpdateResult.REJECT
			}
			if (arg.isTypeImmutable() && currentType !== ArgType.UNKNOWN) {
				// 不改变不可变类型
				if (compareResult == TypeCompareEnum.EQUAL) {
					return TypeUpdateResult.SAME
				}
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Type rejected for {} due to conflict: candidate={}, current={}", arg, candidateType, currentType)
				}
				return TypeUpdateResult.REJECT
			}
			if (compareResult == TypeCompareEnum.WIDER_BY_GENERIC && typeUpdateFlags.isKeepGenerics()) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Type rejected for {}: candidate={} is removing generic from current={}", arg, candidateType, currentType)
				}
				return TypeUpdateResult.REJECT
			}
			if (compareResult.isWider() && !typeUpdateFlags.isAllowWider()) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Type rejected for {}: candidate={} is wider than current={}", arg, candidateType, currentType)
				}
				return TypeUpdateResult.REJECT
			}
			if (candidateType.containsTypeVariable()) {
				// 拒绝含有未知类型变量的候选类型
				val unknownTypeVar = root.getTypeUtils().checkForUnknownTypeVars(updateInfo.mth, candidateType)
				if (unknownTypeVar != null) {
					if (Consts.DEBUG_TYPE_INFERENCE) {
						LOG.debug("Type rejected for {}: candidate: '{}' has unknown type var: '{}'", arg, candidateType, unknownTypeVar)
					}
					return TypeUpdateResult.REJECT
				}
			}
		}
		return null
	}

	private fun updateTypeForSsaVar(updateInfo: TypeUpdateInfo, ssaVar: SSAVar, candidateType: ArgType): TypeUpdateResult? {
		val typeInfo = ssaVar.typeInfo
		val immutableType = ssaVar.immutableType
		if (immutableType != null && immutableType != candidateType) {
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.info("Reject change immutable type {} to {} for {}", immutableType, candidateType, ssaVar)
			}
			return TypeUpdateResult.REJECT
		}
		if (!inBounds(updateInfo, ssaVar, typeInfo.bounds, candidateType)) {
			return TypeUpdateResult.REJECT
		}
		val updateCallback = ArgsListUpdateCallback(this, updateInfo, ssaVar.useList, candidateType, true)
		updateCallback.setFinalResultCallback(
			ITypeUpdateCallback { result ->
				if (result == TypeUpdateResult.REJECT) {
					// 回滚当前 SSA 变量所有寄存器的更新
					updateInfo.rollbackUpdate(ssaVar.assign)
					for (useArg in ssaVar.useList) {
						updateInfo.rollbackUpdate(useArg)
					}
				}
				result
			},
		)
		return queueDirectTypeUpdate(updateInfo, ssaVar.assign, candidateType, updateCallback)
	}

	private fun requestUpdate(updateInfo: TypeUpdateInfo, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		if (updateInfo.isProcessed(arg)) {
			return TypeUpdateResult.CHANGED
		}
		updateInfo.requestUpdate(arg, candidateType)
		val insn = arg.getParentInsn() ?: return TypeUpdateResult.SAME
		val listener = listenerRegistry[insn.type] ?: return TypeUpdateResult.CHANGED
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.debug("Run listener for insn: {}, arg: {}, type: {}", insn.type, arg, candidateType)
		}
		return listener.update(updateInfo, insn, arg, candidateType)
	}

	fun inBounds(bounds: Set<ITypeBound>, candidateType: ArgType): Boolean {
		for (bound in bounds) {
			val boundType = bound.getType()
			if (!checkBound(candidateType, bound, boundType)) {
				return false
			}
		}
		return true
	}

	private fun inBounds(updateInfo: TypeUpdateInfo, ssaVar: SSAVar, bounds: Set<ITypeBound>, candidateType: ArgType): Boolean {
		for (bound in bounds) {
			val boundType = if (bound is ITypeBoundDynamic) {
				bound.getType(updateInfo)
			} else {
				bound.getType()
			}
			if (!checkBound(candidateType, bound, boundType)) {
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.debug("Reject type '{}' for {} by bound: {} from {}", candidateType, ssaVar, boundType, bound)
				}
				return false
			}
		}
		return true
	}

	private fun checkBound(candidateType: ArgType, bound: ITypeBound, boundType: ArgType): Boolean {
		val compareResult = typeCompare.compareTypes(candidateType, boundType)
		return when (compareResult) {
			TypeCompareEnum.EQUAL -> true

			TypeCompareEnum.WIDER -> bound.getBound() != BoundEnum.USE

			TypeCompareEnum.NARROW -> {
				if (bound.getBound() == BoundEnum.ASSIGN) {
					!boundType.isTypeKnown() && checkAssignForUnknown(boundType, candidateType)
				} else {
					true
				}
			}

			TypeCompareEnum.WIDER_BY_GENERIC,
			TypeCompareEnum.NARROW_BY_GENERIC,
			->
				// 外部方法与字段信息不完整，允许对象与带泛型的同对象互换
				true

			TypeCompareEnum.CONFLICT,
			TypeCompareEnum.CONFLICT_BY_GENERIC,
			-> false

			TypeCompareEnum.UNKNOWN -> {
				LOG.warn("Can't compare types, unknown hierarchy: {} and {}", candidateType, boundType)
				typeCompare.compareTypes(candidateType, boundType)
				true
			}
		}
	}

	private fun checkAssignForUnknown(boundType: ArgType, candidateType: ArgType): Boolean {
		if (boundType === ArgType.UNKNOWN) {
			return true
		}
		val candidateArray = candidateType.isArray()
		if (boundType.isArray() && candidateArray) {
			return checkAssignForUnknown(checkNotNull(boundType.getArrayElement()), checkNotNull(candidateType.getArrayElement()))
		}
		if (candidateArray && boundType.contains(PrimitiveType.ARRAY)) {
			return true
		}
		if (candidateType.isObject() && boundType.contains(PrimitiveType.OBJECT)) {
			return true
		}
		if (candidateType.isPrimitive() && boundType.contains(checkNotNull(candidateType.getPrimitiveType()))) {
			return true
		}
		return false
	}

	private fun initListenerRegistry(): Map<InsnType, ITypeListener> {
		val registry = EnumMap<InsnType, ITypeListener>(InsnType::class.java)
		registry[InsnType.CONST] = ITypeListener(this::sameFirstArgListener)
		registry[InsnType.MOVE] = ITypeListener(this::moveListener)
		registry[InsnType.PHI] = ITypeListener(this::allSameListener)
		registry[InsnType.AGET] = ITypeListener(this::arrayGetListener)
		registry[InsnType.APUT] = ITypeListener(this::arrayPutListener)
		registry[InsnType.IF] = ITypeListener(this::ifListener)
		registry[InsnType.ARITH] = ITypeListener(this::arithListener)
		registry[InsnType.NEG] = ITypeListener(this::suggestAllSameListener)
		registry[InsnType.NOT] = ITypeListener(this::suggestAllSameListener)
		registry[InsnType.CHECK_CAST] = ITypeListener(this::checkCastListener)
		registry[InsnType.INVOKE] = ITypeListener(this::invokeListener)
		registry[InsnType.CONSTRUCTOR] = ITypeListener(this::invokeListener)
		return registry
	}

	private fun invokeListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val invoke = insn as BaseInvokeNode
		if (isAssign(invoke, arg)) {
			// TODO: 实现从结果到实例的反向类型传播
			return TypeUpdateResult.SAME
		}
		if (invoke.getInstanceArg() === arg) {
			val methodDetails = root.getMethodUtils().getMethodDetails(invoke) ?: return TypeUpdateResult.SAME
			val typeUtils = root.getTypeUtils()
			val knownTypeVars = typeUtils.getKnownTypeVarsAtMethod(updateInfo.mth)
			val typeVarsMap = typeUtils.getTypeVariablesMapping(candidateType)

			val returnType = methodDetails.getReturnType()
			val argTypes = methodDetails.getArgTypes()
			val argsCount = argTypes.size

			val getReturnType: () -> ArgType?
			val getArgType: (Int) -> ArgType?
			if (typeVarsMap.isEmpty()) {
				// 无法解析泛型 => 原样使用
				getReturnType = { returnType }
				getArgType = { argNum -> argTypes[argNum] }
			} else {
				// 应用前先解析泛型
				getReturnType = { typeUtils.replaceTypeVariablesUsingMap(returnType, typeVarsMap) }
				getArgType = { argNum -> typeUtils.replaceClassGenerics(candidateType, argTypes[argNum]) }
			}
			return InvokeUpdateCallback(this, updateInfo, invoke, argsCount, knownTypeVars, getReturnType, getArgType).runQueue()
		}
		return TypeUpdateResult.SAME
	}

	private fun sameFirstArgListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val changeArg = if (isAssign(insn, arg)) insn.getArg(0) else checkNotNull(insn.getResult())
		if (updateInfo.hasUpdateWithType(changeArg, candidateType)) {
			return TypeUpdateResult.CHANGED
		}
		return queueTypeUpdate(updateInfo, changeArg, candidateType, null)
	}

	private fun moveListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val result = insn.getResult() ?: return TypeUpdateResult.CHANGED
		val assignChanged = isAssign(insn, arg)
		val changeArg = if (assignChanged) insn.getArg(0) else result

		// 允许结果更宽
		val cmp = typeCompare.compareTypes(candidateType, changeArg.getType())
		val correctType = cmp.isEqual() || (if (assignChanged) cmp.isWider() else cmp.isNarrow())

		return queueTypeUpdate(
			updateInfo,
			changeArg,
			candidateType,
			ITypeUpdateCallback { r ->
				if (r == TypeUpdateResult.SAME && !correctType) {
					if (Consts.DEBUG_TYPE_INFERENCE) {
						LOG.debug(
							"Move insn types mismatch: {} -> {}, change arg: {}, insn: {}",
							candidateType,
							changeArg.getType(),
							changeArg,
							insn,
						)
					}
					return@ITypeUpdateCallback TypeUpdateResult.REJECT
				}
				if (r == TypeUpdateResult.REJECT && correctType) {
					return@ITypeUpdateCallback TypeUpdateResult.CHANGED
				}
				r
			},
		)
	}

	/** 所有参数必须同类型。 */
	private fun allSameListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		if (!isAssign(insn, arg)) {
			return queueTypeUpdate(updateInfo, checkNotNull(insn.getResult()), candidateType, null)
		}
		// 用相同类型更新其它参数
		val updateCallback = ArgsListUpdateCallback(this, updateInfo, insn.argList, candidateType, false)
		updateCallback.setArgsFilter { a -> a !== arg }
		return updateCallback.runFirstQueue()
	}

	private fun arithListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val arithInsn = insn as ArithNode
		if (candidateType === ArgType.BOOLEAN && arithInsn.op.isBitOp()) {
			// 强制所有参数为 boolean
			return allSameListener(updateInfo, insn, arg, candidateType)
		}
		return suggestAllSameListener(updateInfo, insn, arg, candidateType)
	}

	/**
	 * 尝试把候选类型设置给所有参数，遇到拒绝也不失败。
	 */
	private fun suggestAllSameListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val updateCallback = ArgsListUpdateCallback(this, updateInfo, insn.argList, candidateType, false)
		updateCallback.setArgsFilter { a -> a !== arg }
		updateCallback.setIgnoreReject(true)
		if (!isAssign(insn, arg)) {
			val resultArg = insn.getResult()
			if (resultArg != null) {
				// 从结果开始
				return queueTypeUpdate(updateInfo, resultArg, candidateType, updateCallback)
			}
		}
		// 从第一个参数开始
		return updateCallback.runFirstQueue()
	}

	private fun checkCastListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val checkCast = insn as IndexInsnNode
		if (isAssign(insn, arg)) {
			val insnArg = insn.getArg(0)
			return queueTypeUpdate(
				updateInfo,
				insnArg,
				candidateType,
				ITypeUpdateCallback { r -> if (r == TypeUpdateResult.REJECT) TypeUpdateResult.SAME else r },
			)
		}
		val castType = checkCast.indexAsType
		val res = typeCompare.compareTypes(candidateType, castType)
		if (res == TypeCompareEnum.CONFLICT) {
			// 允许接口之间的互转
			if (!isInterfaces(candidateType, castType)) {
				return TypeUpdateResult.REJECT
			}
		}
		if (res == TypeCompareEnum.CONFLICT_BY_GENERIC) {
			if (!insn.contains(AFlag.SOFT_CAST)) {
				return TypeUpdateResult.REJECT
			}
		}
		if (res == TypeCompareEnum.NARROW_BY_GENERIC && candidateType.containsGeneric()) {
			// 把泛型类型传播到结果
			return queueTypeUpdate(updateInfo, checkNotNull(checkCast.getResult()), candidateType, null)
		}
		val currentType = checkCast.getArg(0).getType()
		return if (candidateType == currentType) TypeUpdateResult.SAME else TypeUpdateResult.CHANGED
	}

	private fun isInterfaces(firstType: ArgType, secondType: ArgType): Boolean {
		if (!firstType.isObject() || !secondType.isObject()) {
			return false
		}
		val clsp = checkNotNull(root.getClsp())
		val firstCls: ClspClass? = clsp.getClsDetails(firstType)
		val secondCls: ClspClass? = clsp.getClsDetails(secondType)
		if (firstCls != null && !firstCls.isInterface()) {
			return false
		}
		if (secondCls != null && !secondCls.isInterface()) {
			return false
		}
		if (firstCls == null || secondCls == null) {
			return true
		}
		return secondCls.isInterface() && firstCls.isInterface()
	}

	private fun arrayGetListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		if (isAssign(insn, arg)) {
			return queueTypeUpdate(
				updateInfo,
				insn.getArg(0),
				ArgType.array(candidateType),
				ITypeUpdateCallback { result ->
					if (result == TypeUpdateResult.REJECT) {
						val arrType = insn.getArg(0).getType()
						if (arrType.isTypeKnown() && arrType.isArray() && checkNotNull(arrType.getArrayElement()).isPrimitive()) {
							val compResult = typeCompare.compareTypes(candidateType, checkNotNull(arrType.getArrayElement()))
							if (compResult == TypeCompareEnum.WIDER) {
								// 允许基本类型的隐式向上转型（int a = byteArr[n]）
								return@ITypeUpdateCallback TypeUpdateResult.CHANGED
							}
						}
					}
					result
				},
			)
		}
		val arrArg = insn.getArg(0)
		if (arrArg === arg) {
			val arrayElement = candidateType.getArrayElement() ?: return TypeUpdateResult.REJECT
			return queueTypeUpdate(
				updateInfo,
				checkNotNull(insn.getResult()),
				arrayElement,
				ITypeUpdateCallback { result ->
					if (result == TypeUpdateResult.REJECT) {
						val resType = checkNotNull(insn.getResult()).getType()
						if (resType.isTypeKnown() && resType.isPrimitive()) {
							val compResult = typeCompare.compareTypes(resType, arrayElement)
							if (compResult == TypeCompareEnum.WIDER) {
								// 允许基本类型的隐式向上转型（int a = byteArr[n]）
								return@ITypeUpdateCallback TypeUpdateResult.CHANGED
							}
						}
					}
					result
				},
			)
		}
		// 下标参数
		return TypeUpdateResult.SAME
	}

	private fun arrayPutListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val arrArg = insn.getArg(0)
		val putArg = insn.getArg(2)
		if (arrArg === arg) {
			val arrayElement = candidateType.getArrayElement() ?: return TypeUpdateResult.REJECT
			return queueTypeUpdate(
				updateInfo,
				putArg,
				arrayElement,
				ITypeUpdateCallback { result ->
					if (result == TypeUpdateResult.REJECT) {
						val putType = putArg.getType()
						if (putType.isTypeKnown()) {
							val compResult = typeCompare.compareTypes(arrayElement, putType)
							if (compResult == TypeCompareEnum.WIDER || compResult == TypeCompareEnum.WIDER_BY_GENERIC) {
								// 允许更宽的结果（如 Object[] 放任意对象、int[] 放 byte）
								return@ITypeUpdateCallback TypeUpdateResult.CHANGED
							}
						}
					}
					result
				},
			)
		}
		if (arrArg === putArg) {
			return queueTypeUpdate(updateInfo, arrArg, ArgType.array(candidateType), null)
		}
		// 下标
		return TypeUpdateResult.SAME
	}

	private fun ifListener(updateInfo: TypeUpdateInfo, insn: InsnNode, arg: InsnArg, candidateType: ArgType): TypeUpdateResult? {
		val firstArg = insn.getArg(0)
		val secondArg = insn.getArg(1)
		val updateArg = if (firstArg === arg) secondArg else firstArg
		return queueTypeUpdate(
			updateInfo,
			updateArg,
			candidateType,
			ITypeUpdateCallback { result ->
				if (result == TypeUpdateResult.REJECT) {
					// 对对象/数组做软检查：不比较精确类型
					val updateArgType = updateArg.getType()
					if (candidateType.isObject() && updateArgType.canBeObject()) {
						return@ITypeUpdateCallback TypeUpdateResult.SAME
					}
					if (candidateType.isArray() && updateArgType.canBeArray()) {
						return@ITypeUpdateCallback TypeUpdateResult.SAME
					}
					if (candidateType.isPrimitive()) {
						if (updateArgType.canBePrimitive(checkNotNull(candidateType.getPrimitiveType()))) {
							return@ITypeUpdateCallback TypeUpdateResult.SAME
						}
						if (updateArgType.isTypeKnown() && candidateType.regCount == updateArgType.regCount) {
							return@ITypeUpdateCallback TypeUpdateResult.SAME
						}
					}
				}
				result
			},
		)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TypeUpdate::class.java)

		/** 沿回调链回传结果；某个回调返回 null 表示需等待后续结果，此时把回调放回队列。 */
		private fun processCallbacks(updateInfo: TypeUpdateInfo, result: TypeUpdateResult): TypeUpdateResult? {
			var current: TypeUpdateResult = result
			while (true) {
				val cbReq = updateInfo.pollNextCallback() ?: return current
				val callback = checkNotNull(cbReq.callback)
				val next = callback.updateCallback(current)
				if (next == null) {
					// 无结果：把回调放回队列，等结果算出后再执行
					updateInfo.saveCallback(cbReq)
					return null
				}
				current = next
				if (current == TypeUpdateResult.REJECT) {
					updateInfo.rollbackUpdate(cbReq.arg)
				}
				// 继续处理下一个回调
			}
		}

		private fun isAssign(insn: InsnNode, arg: InsnArg): Boolean = insn.getResult() === arg
	}
}
