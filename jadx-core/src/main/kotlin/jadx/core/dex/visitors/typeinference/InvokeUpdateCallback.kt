package jadx.core.dex.visitors.typeinference

import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.RegisterArg
import java.util.function.Function
import java.util.function.Supplier

/**
 * 方法调用（invoke）的类型更新回调。
 *
 * **算法意图**：当 invoke 的实例/参数类型被更新时，需要把类型同时传播到：
 * - 返回值（[runQueue] 先处理结果参数）；
 * - 每个可变的参数（[getNextArg] 逐个取出）。
 * 泛型返回类型/参数类型由外部通过 [getReturnType]/[getArgType] 回调动态计算。
 *
 * **Kotlin 转换说明**：
 * - `Supplier`/`Function` 保持 `java.util.function` 类型，Java 调用方零改动；
 * - [updateArg]/[updateType] 在真正处理 REJECT 前必定已赋值，故用 `lateinit` 表达。
 */
class InvokeUpdateCallback(
	private val typeUpdate: TypeUpdate,
	private val updateInfo: TypeUpdateInfo,
	private val invoke: BaseInvokeNode,
	private val argsCount: Int,
	private val knownTypeVars: Set<ArgType>,
	private val getReturnType: Supplier<ArgType?>,
	private val getArgType: Function<Int, ArgType?>,
) : ITypeUpdateCallback {

	private var isAssign = false
	private var allSame = true
	private var currentArg = -1

	private lateinit var updateArg: InsnArg
	private lateinit var updateType: ArgType
	private var firstQueue = false

	override fun updateCallback(result: TypeUpdateResult): TypeUpdateResult? {
		var res = result
		while (true) {
			when (res) {
				TypeUpdateResult.CHANGED -> allSame = false

				TypeUpdateResult.REJECT -> {
					// 拒绝时要看方向是否“合理”：赋值方向允许更宽，使用方向允许更窄
					val compare = typeUpdate.typeCompare.compareTypes(updateType, updateArg.getType())
					if (if (isAssign) compare.isWider() else compare.isNarrow()) {
						return TypeUpdateResult.REJECT
					}
				}

				else -> {
				}
			}
			if (!getNextArg()) {
				return if (allSame) TypeUpdateResult.SAME else TypeUpdateResult.CHANGED
			}
			// 只有第一个排队更新把本回调继续挂上
			val cb: ITypeUpdateCallback?
			if (firstQueue) {
				cb = this
				firstQueue = false
			} else {
				cb = null
			}
			val nextResult = typeUpdate.queueTypeUpdate(updateInfo, updateArg, updateType, cb)
			if (nextResult == null) {
				return null
			}
			res = nextResult
		}
	}

	fun runQueue(): TypeUpdateResult? {
		firstQueue = true
		var result = TypeUpdateResult.SAME
		val resultArg = invoke.getResult()
		if (resultArg != null && !resultArg.isTypeImmutable()) {
			val returnType = checkType(knownTypeVars, getReturnType.get())
			if (returnType != null) {
				updateArg = resultArg
				updateType = returnType
				isAssign = true
				firstQueue = false
				val res = typeUpdate.queueTypeUpdate(updateInfo, updateArg, updateType, this)
				if (res == null) {
					return null
				}
				result = res
			}
		}
		return updateCallback(result)
	}

	private fun getNextArg(): Boolean {
		while (true) {
			currentArg++
			val i = currentArg
			if (i >= argsCount) {
				return false
			}
			val argOffset = invoke.getFirstArgOffset()
			val invokeArg = invoke.getArg(argOffset + i)
			if (!invokeArg.isTypeImmutable()) {
				val argType = checkType(knownTypeVars, getArgType.apply(i))
				if (argType != null) {
					updateArg = invokeArg
					updateType = argType
					isAssign = false
					return true
				}
			}
		}
	}

	private fun checkType(knownTypeVars: Set<ArgType>, type: ArgType?): ArgType? {
		if (type == null) {
			return null
		}
		if (type.isWildcard()) {
			return null
		}
		if (type.containsTypeVariable()) {
			if (knownTypeVars.isEmpty()) {
				return null
			}
			val hasUnknown = type.visitTypes(this::isUnknown)
			if (hasUnknown != null) {
				return null
			}
		}
		return type
	}

	private fun isUnknown(t: ArgType): Boolean? {
		if (t.isGenericType() && !knownTypeVars.contains(t)) {
			return true
		}
		return null
	}
}
