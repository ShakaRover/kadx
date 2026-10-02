package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockPathTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.PredecessorBlockPathTraverserHandler

/**
 * “当前块已无剩余指令”的状态：需要沿前驱继续向上搜索，寻找可匹配的重复指令。
 *
 * 实现 [ISourceBlockState] 是为了把“从哪个块出发”告诉前驱处理器。
 *
 * **Kotlin 转换说明**：`getFactory` 供 Java 侧静态调用（`@JvmStatic`）；
 * `getNextHandler()` 协变返回 [AbstractBlockPathTraverserHandler]（原 Java 即如此）。
 */
class NoBlockTraverserState(
	state: TraverserActivePathState,
	private val centralityState: CentralityState,
	private val sourceBlock: BlockNode,
) : TraverserState(state),
	ISourceBlockState {

	override fun getNextHandler(): AbstractBlockPathTraverserHandler = PredecessorBlockPathTraverserHandler(this)

	override fun getCompareState(): ComparisonState = ComparisonState.NOT_READY

	override fun isTerminal(): Boolean = false

	override fun getUnderlyingCentralityState(): CentralityState = centralityState

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo? = null

	override fun getSourceBlock(): BlockNode = sourceBlock

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState {
		val dCentralityState = centralityState.duplicate()
		return NoBlockTraverserState(comparatorState, dCentralityState, sourceBlock)
	}

	companion object {
		@JvmStatic
		fun getFactory(centralityState: CentralityState, sourceBlock: BlockNode): TraverserStateFactory<NoBlockTraverserState> = NoBlockStateFactory(centralityState, sourceBlock)
	}

	private class NoBlockStateFactory(
		private val centralityState: CentralityState,
		private val sourceBlock: BlockNode,
	) : TraverserStateFactory<NoBlockTraverserState>() {

		override fun generateInternalState(state: TraverserActivePathState): NoBlockTraverserState = NoBlockTraverserState(state, centralityState, sourceBlock)
	}
}
