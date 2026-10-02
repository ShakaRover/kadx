package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.MergePathActivePathTraverserHandler

/**
 * “已识别出一个作用域及其终点”的状态。
 *
 * **含义**：当某个 try 出口的作用域被确定后，我们需要把该作用域内的多条根路径
 * 与 finally 侧一一配对合并。[roots] 是作用域内的根块，[scopeTerminator] 是作用域终点。
 *
 * **Kotlin 转换说明**：`getFactory` 供 Java 侧静态调用（`@JvmStatic`）。
 */
class IdentifiedScopeWithTerminatorTraverserState(
	state: TraverserActivePathState,
	private val centralityState: CentralityState,
	private val roots: List<BlockNode>,
	private val scopeTerminator: BlockNode,
) : TraverserState(state) {

	override fun getNextHandler(): AbstractBlockTraverserHandler = MergePathActivePathTraverserHandler(getComparatorState())

	override fun getCompareState(): ComparisonState = ComparisonState.READY_TO_COMPARE

	override fun isTerminal(): Boolean = false

	override fun getUnderlyingCentralityState(): CentralityState = centralityState

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo? = null

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState = IdentifiedScopeWithTerminatorTraverserState(comparatorState, centralityState, roots, scopeTerminator)

	fun getTerminus(): BlockNode = scopeTerminator

	fun getRoots(): List<BlockNode> = roots

	companion object {
		@JvmStatic
		fun getFactory(
			centralityState: CentralityState,
			roots: List<BlockNode>,
			scopeTerminator: BlockNode,
		): TraverserStateFactory<IdentifiedScopeWithTerminatorTraverserState> = IdentifiedScopeWithTerminatorStateFactory(centralityState, roots, scopeTerminator)
	}

	private class IdentifiedScopeWithTerminatorStateFactory(
		private val centralityState: CentralityState,
		private val roots: List<BlockNode>,
		private val scopeTerminator: BlockNode,
	) : TraverserStateFactory<IdentifiedScopeWithTerminatorTraverserState>() {

		override fun generateInternalState(state: TraverserActivePathState): IdentifiedScopeWithTerminatorTraverserState = IdentifiedScopeWithTerminatorTraverserState(state, centralityState, roots, scopeTerminator)
	}
}
