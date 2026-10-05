package kadx.core.dex.visitors.finaly.traverser.state

import kadx.core.dex.visitors.finaly.CentralityState
import kadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import kadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockTraverserHandler

/**
 * “从缓存恢复”的状态：表示该块对之前已经搜索过，直接复用缓存中的结果。
 *
 * 它是一个终止状态（[isTerminal] 为 true），但内部持有 [underlying]（缓存里的原始状态），
 * 当原始状态本身也终止时，[canContinue] 会返回 true，允许调用方继续沿用缓存。
 *
 * **Kotlin 转换说明**：`getFactory` 供 Java 侧静态调用（`@JvmStatic`）。
 */
class RecoveredFromCacheTraverserState(private val underlying: TraverserState) : TraverserState(underlying.comparatorState) {

	override fun getNextHandler(): AbstractBlockTraverserHandler? = null

	override fun getCompareState(): ComparisonState = ComparisonState.NOT_READY

	override fun isTerminal(): Boolean = true

	override fun getUnderlyingCentralityState(): CentralityState? = null

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo? = null

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState = RecoveredFromCacheTraverserState(underlying)

	fun getUnderlying(): TraverserState = underlying

	fun canContinue(): Boolean = underlying.isTerminal()

	companion object {
		fun getFactory(underlying: TraverserState): TraverserStateFactory<RecoveredFromCacheTraverserState> = RecoveredFromCacheStateFactory(underlying)
	}

	private class RecoveredFromCacheStateFactory(private val underlying: TraverserState) : TraverserStateFactory<RecoveredFromCacheTraverserState>() {

		override fun generateInternalState(state: TraverserActivePathState): RecoveredFromCacheTraverserState = RecoveredFromCacheTraverserState(underlying)
	}
}
