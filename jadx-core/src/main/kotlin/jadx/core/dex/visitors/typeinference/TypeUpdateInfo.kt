package jadx.core.dex.visitors.typeinference

import jadx.api.JadxArgs
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.ListUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxOverflowException
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.IdentityHashMap

/**
 * 一次类型更新过程中的中间状态。
 *
 * **算法意图**：类型传播是一轮“建议-验证-应用”的批处理：
 * - [queue] 保存待处理的更新请求（LIFO，用 [pollNextRequest] 取）；
 * - [callbackQueue] 保存需要在结果出来后回调的请求；
 * - [updateMap] 用**按引用比较**的 [IdentityHashMap] 记录每个参数最终要设置的类型，
 *   并用递增 [updateSeq] 保证顺序；
 * - 超过 [updatesLimitCount] 次更新说明推导陷入循环，抛 [JadxOverflowException]。
 *
 * **Kotlin 转换说明**：
 * - [IdentityHashMap] 保留原 Java 的“按引用”语义；
 * - `removeIf` 改写为显式迭代器删除，避免集合 API 差异；
 * - `stream().sorted()` 改为 [getSortedUpdates] 普通排序。
 */
class TypeUpdateInfo(
	val mth: MethodNode,
	val flags: TypeUpdateFlags,
	args: JadxArgs,
) {
	private val updateMap: MutableMap<InsnArg, TypeUpdateEntry> = IdentityHashMap()
	private val queue: MutableList<TypeUpdateRequest> = ArrayList()
	private val callbackQueue: MutableList<TypeUpdateRequest> = ArrayList()
	private val updatesLimitCount: Int = mth.insnsCount * args.typeUpdatesLimitCount
	private var updateSeq = 0

	fun queueRequest(request: TypeUpdateRequest) {
		queue.add(request)
	}

	fun saveCallback(request: TypeUpdateRequest) {
		if (request.callback != null) {
			callbackQueue.add(request)
		}
	}

	fun pollNextRequest(): TypeUpdateRequest? = ListUtils.removeLast(queue)

	fun pollNextCallback(): TypeUpdateRequest? = ListUtils.removeLast(callbackQueue)

	/** 记录“把 [arg] 改成 [changeType]”。同一参数重复更新视为异常。 */
	fun requestUpdate(arg: InsnArg, changeType: ArgType) {
		val prev = updateMap.put(arg, TypeUpdateEntry(updateSeq++, arg, changeType))
		if (prev != null) {
			throw JadxRuntimeException(
				"Unexpected type update override for arg: " + arg +
					" types: prev=" + prev.type + ", new=" + changeType +
					", insn: " + arg.getParentInsn(),
			)
		}
		if (updateSeq > updatesLimitCount) {
			throw JadxOverflowException(
				"Type inference error: updates count limit reached" +
					" with updateSeq = " + updateSeq + ". Try increasing type updates limit count.",
			)
		}
		if (updateSeq % 100 == 0) {
			// 每 100 次更新检查一次线程中断（每次都查开销太大）
			Utils.checkThreadInterrupt()
		}
	}

	/** 回滚对 [arg] 的更新，并丢弃所有更晚（seq 更大）的更新。 */
	fun rollbackUpdate(arg: InsnArg) {
		val removed = updateMap.remove(arg)
		if (removed != null) {
			val seq = removed.seq
			val iter = updateMap.values.iterator()
			while (iter.hasNext()) {
				if (iter.next().seq > seq) {
					iter.remove()
				}
			}
		}
	}

	/** 按 seq 升序把记录的更新真正写回各参数类型。 */
	fun applyUpdates() {
		for (upd in sortedUpdates) {
			upd.arg.setType(upd.type)
		}
	}

	fun isProcessed(arg: InsnArg): Boolean = updateMap.containsKey(arg)

	fun hasUpdateWithType(arg: InsnArg, type: ArgType): Boolean {
		val updateEntry = updateMap[arg]
		if (updateEntry != null) {
			return updateEntry.type == type
		}
		return false
	}

	fun getType(arg: InsnArg): ArgType {
		val updateEntry = updateMap[arg]
		if (updateEntry != null) {
			return updateEntry.type
		}
		return arg.getType()
	}

	fun isEmpty(): Boolean = updateMap.isEmpty()

	val sortedUpdates: List<TypeUpdateEntry> get() = updateMap.values.sorted()

	override fun toString(): String = "TypeUpdateInfo{$flags $sortedUpdates}"
}
