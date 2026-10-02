package jadx.core.dex.visitors.finaly.traverser.factory

import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * “复制状态”工厂：把一个已有状态克隆到新的活动路径上。
 *
 * **用途**：当一条路径分叉时，另一侧（不参与推进的一侧）需要原样复制一份状态到新路径，
 * 以避免分支之间共享同一个状态对象。
 *
 * **Kotlin 转换说明**：原 Java 用 `(Class<? extends T>) baseState.getClass()` 做强制转换，
 * Kotlin 的 `javaClass` 已经能推导出 `Class<T>`，因此无需 `@Suppress`，[generateInternalState]
 * 的返回类型仍是 [TraverserState] 的子类型 T。
 */
class DuplicatedTraverserStateFactory<T : TraverserState>(private val baseState: T) : TraverserStateFactory<T>() {

	override fun generateInternalState(state: TraverserActivePathState): T {
		val baseStateClass = baseState.javaClass
		val duplicated: TraverserState = baseState.duplicate(state)
		if (!baseStateClass.isInstance(duplicated)) {
			throw JadxRuntimeException(
				"A state of class " + baseState.javaClass + " has duplicated to produce a class of " + duplicated.javaClass,
			)
		}
		return baseStateClass.cast(duplicated)
	}
}
