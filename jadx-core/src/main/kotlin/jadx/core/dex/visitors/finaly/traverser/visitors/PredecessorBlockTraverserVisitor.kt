package jadx.core.dex.visitors.finaly.traverser.visitors

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.GlobalTraverserSourceState
import jadx.core.dex.visitors.finaly.traverser.state.NewBlockTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.dex.visitors.finaly.traverser.state.UnknownAdvanceStrategyTraverserState
import jadx.core.utils.ListUtils

/**
 * 前驱块访问器：根据“作用域内的前驱数量”决定下一步状态。
 *
 * **分支逻辑**：
 * - 0 个前驱：路径结束，返回终止状态 [TerminalTraverserState]（END_OF_PATH）；
 * - 1 个前驱：进入该前驱块，返回 [NewBlockTraverserState]；
 * - 多个前驱：推进策略未知，返回 [UnknownAdvanceStrategyTraverserState] 交给合并处理器。
 *
 * **Kotlin 转换说明**：原 Java 用 `ListUtils.filter(predecessors, globalState::isBlockContained)`，
 * Kotlin 侧改为尾随 lambda，语义一致（过滤出属于当前子图的前驱）。
 */
class PredecessorBlockTraverserVisitor(state: TraverserState) : AbstractBlockTraverserVisitor(state) {

	override fun visit(block: BlockNode): TraverserState {
		val currentState: TraverserState = getState()
		val centralityState: CentralityState = currentState.centralityState
		val globalState: GlobalTraverserSourceState = currentState.globalState

		val predecessors: List<BlockNode> = block.getPredecessors()
		val containedPredecessors: List<BlockNode> = ListUtils.filter(predecessors) { globalState.isBlockContained(it) }
		val predecessorsCount = containedPredecessors.size
		return when (predecessorsCount) {
			0 -> TerminalTraverserState(getComparator(), TerminalTraverserState.TerminationReason.END_OF_PATH)

			1 -> {
				val nextBlock: BlockNode = containedPredecessors[0]
				val blockInfo = TraverserBlockInfo(nextBlock)
				NewBlockTraverserState(getComparator(), centralityState, blockInfo)
			}

			else -> UnknownAdvanceStrategyTraverserState(getComparator(), centralityState, containedPredecessors)
		}
	}
}
