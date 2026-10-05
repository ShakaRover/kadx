package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg

/**
 * 类型更新回调：把同一个候选类型依次应用到一组参数上。
 *
 * **算法意图**：某些指令要求“所有参数类型一致”（如 PHI、位运算）。
 * 本回调持有参数迭代器，在每次子更新完成后取出下一个参数继续排队更新，
 * 并记录整体结果：
 * - 全部子更新都是 SAME → 最终返回 SAME；
 * - 只要有一个发生改变 → 最终返回 CHANGED；
 * - 遇到 REJECT 时默认直接返回 REJECT（可用 [setIgnoreReject] 忽略）。
 *
 * **Kotlin 转换说明**：
 * - 泛型上界 `<T extends InsnArg>` 写为 `<T : InsnArg>`；
 * - 过滤回调改为 Kotlin 函数类型 `(T) -> Boolean`。
 */
class ArgsListUpdateCallback<T : InsnArg>(
	private val typeUpdate: TypeUpdate,
	private val updateInfo: TypeUpdateInfo,
	args: List<T>,
	private val candidateType: ArgType,
	private val direct: Boolean,
) : ITypeUpdateCallback {

	private val argsIterator: Iterator<T> = args.iterator()

	private var argsFilter: ((T) -> Boolean)? = null
	private var finalResultCallback: ITypeUpdateCallback? = null
	private var ignoreReject = false

	private var allSame = true
	private var firstQueue = false

	override fun updateCallback(result: TypeUpdateResult): TypeUpdateResult? {
		var res = result
		while (true) {
			if (!ignoreReject) {
				if (res == TypeUpdateResult.REJECT) {
					return finalResult(res)
				}
			}
			if (res != TypeUpdateResult.SAME) {
				allSame = false
			}
			val next = nextArg
			if (next == null) {
				return finalResult(if (allSame) TypeUpdateResult.SAME else TypeUpdateResult.CHANGED)
			}
			val nextResult = queueUpdate(next)
			if (nextResult == null) {
				// 保持本回调，等待后续结果
				return null
			}
			res = nextResult
		}
	}

	private fun queueUpdate(next: T): TypeUpdateResult? {
		// 只有第一个排队更新把本回调继续挂上，避免重复回调
		val cb: ITypeUpdateCallback?
		if (firstQueue) {
			cb = this
			firstQueue = false
		} else {
			cb = null
		}
		return if (direct) {
			typeUpdate.queueDirectTypeUpdate(updateInfo, next, candidateType, cb)
		} else {
			typeUpdate.queueTypeUpdate(updateInfo, next, candidateType, cb)
		}
	}

	fun runFirstQueue(): TypeUpdateResult? {
		firstQueue = true
		return updateCallback(TypeUpdateResult.SAME)
	}

	fun setFinalResultCallback(finalResultCallback: ITypeUpdateCallback?) {
		this.finalResultCallback = finalResultCallback
	}

	fun setArgsFilter(argsFilter: ((T) -> Boolean)?) {
		this.argsFilter = argsFilter
	}

	fun setIgnoreReject(ignoreReject: Boolean) {
		this.ignoreReject = ignoreReject
	}

	private fun finalResult(result: TypeUpdateResult): TypeUpdateResult? {
		val callback = finalResultCallback
		if (callback != null) {
			return callback.updateCallback(result)
		}
		return result
	}

	private val nextArg: T? get() {
		val filter = argsFilter
		while (true) {
			if (!argsIterator.hasNext()) {
				return null
			}
			val next = argsIterator.next()
			if (filter == null || filter(next)) {
				return next
			}
		}
	}

	override fun toString(): String = "ArgsListUpdateCallback"
}
