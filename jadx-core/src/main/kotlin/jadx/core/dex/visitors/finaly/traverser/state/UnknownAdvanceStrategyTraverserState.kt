package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractActivePathTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.PredecessorMergeActivePathTraverserHandler

/**
 * “前驱推进策略未知”的状态：当前块有多个前驱，需要由
 * [PredecessorMergeActivePathTraverserHandler] 决定如何分叉或合并。
 *
 * [nextBlocks] 是待处理的多个前驱块。
 *
 * **Kotlin 转换说明**：`getNextHandler()` 协变返回 [AbstractActivePathTraverserHandler]。
 */
class UnknownAdvanceStrategyTraverserState(
	state: TraverserActivePathState,
	private val centralityState: CentralityState,
	private val nextBlocks: List<BlockNode>,
) : TraverserState(state) {

	override fun getNextHandler(): AbstractActivePathTraverserHandler = PredecessorMergeActivePathTraverserHandler(getComparatorState())

	override fun getCompareState(): ComparisonState = ComparisonState.READY_TO_COMPARE

	override fun isTerminal(): Boolean = false

	override fun getUnderlyingCentralityState(): CentralityState = centralityState

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo? = null

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState {
		val dCentralityState = centralityState.duplicate()
		val dNextBlocks = ArrayList(nextBlocks)
		return UnknownAdvanceStrategyTraverserState(comparatorState, dCentralityState, dNextBlocks)
	}

	fun getNextBlocks(): List<BlockNode> = nextBlocks
}
