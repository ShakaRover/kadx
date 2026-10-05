package kadx.core.dex.visitors.finaly.traverser.handlers

import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.finaly.CentralityState
import kadx.core.dex.visitors.finaly.traverser.GlobalTraverserSourceState
import kadx.core.dex.visitors.finaly.traverser.TraverserException
import kadx.core.dex.visitors.finaly.traverser.factory.DuplicatedTraverserStateFactory
import kadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import kadx.core.dex.visitors.finaly.traverser.state.IdentifiedScopeWithTerminatorTraverserState
import kadx.core.dex.visitors.finaly.traverser.state.NewBlockTraverserState
import kadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState
import kadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import kadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import kadx.core.dex.visitors.finaly.traverser.state.TraverserState
import kadx.core.dex.visitors.finaly.traverser.state.UnknownAdvanceStrategyTraverserState
import kadx.core.utils.BlockUtils

/**
 * “前驱合并”处理器：处理当前块有多个前驱时的分叉/合并策略。
 *
 * **核心决策**：
 * - 若 finally 侧与候选侧都“可以比较”（READY_TO_COMPARE），则把两侧作用域直接合并
 *   （[mergeScopes]）；
 * - 否则，只让需要推进的一侧沿其多个前驱分叉出多条路径，另一侧复制到每条路径
 *   （[duplicateForPaths]）。
 *
 * **Kotlin 转换说明**：原 Java 的 `==` 比较枚举用 `==`（枚举值语义相同），
 * 对象引用比较用 `===`；私有辅助方法保持私有。
 */
class PredecessorMergeActivePathTraverserHandler(initialState: TraverserActivePathState) : AbstractActivePathTraverserHandler(initialState) {

	@Throws(TraverserException::class)
	override fun handle(): List<TraverserActivePathState> {
		// 此时处理器持有“请求前驱合并”的路径块状态。若另一侧也请求合并，就合并两者；
		// 否则把活动路径拆分以支持多条路径。
		val comparator = comparator
		val finallyState: TraverserState = comparator.getFinallyState()
		val candidateState: TraverserState = comparator.getCandidateState()

		val finallyNeedsDuplicate = finallyState.getCompareState() == TraverserState.ComparisonState.READY_TO_COMPARE
		val candidateNeedsDuplicate = candidateState.getCompareState() == TraverserState.ComparisonState.READY_TO_COMPARE
		val shouldMerge = finallyNeedsDuplicate && candidateNeedsDuplicate

		if (shouldMerge) {
			return mergeScopes(
				finallyState as UnknownAdvanceStrategyTraverserState,
				candidateState as UnknownAdvanceStrategyTraverserState,
			)
		} else {
			val advancingState: UnknownAdvanceStrategyTraverserState
			val otherState: TraverserState
			if (finallyNeedsDuplicate) {
				advancingState = finallyState as UnknownAdvanceStrategyTraverserState
				otherState = candidateState
			} else {
				advancingState = candidateState as UnknownAdvanceStrategyTraverserState
				otherState = finallyState
			}
			return duplicateForPaths(comparator, advancingState, otherState, finallyNeedsDuplicate)
		}
	}

	private fun orderBlocks(blocks: List<BlockNode>): List<BlockNode> {
		val dup = ArrayList(blocks)
		// 原 Java 中按 CId 排序的代码被注释掉了，这里同样保留不排序。
		return dup
	}

	@Throws(TraverserException::class)
	private fun mergeScopes(
		finallyState: UnknownAdvanceStrategyTraverserState,
		candidateState: UnknownAdvanceStrategyTraverserState,
	): List<TraverserActivePathState> {
		val finallyBlocks: List<BlockNode> = finallyState.nextBlocks
		val candidateBlocks: List<BlockNode> = candidateState.nextBlocks

		val finallyBlocksSize = finallyBlocks.size
		val candidateBlocksSize = candidateBlocks.size

		val states: List<TraverserActivePathState>
		if (candidateBlocksSize % finallyBlocksSize == 0 && candidateBlocksSize == finallyBlocksSize) {
			val finallyBlocksOrdered = orderBlocks(finallyBlocks)
			val candidateBlocksOrdered = orderBlocks(candidateBlocks)

			val duplicationCount = candidateBlocksSize / finallyBlocksSize

			val statesList = ArrayList<TraverserActivePathState>(duplicationCount)
			for (i in 0 until duplicationCount) {
				val candidateBlocksSubset = ArrayList<BlockNode>(finallyBlocksSize)
				for (j in 0 until finallyBlocksSize) {
					candidateBlocksSubset.add(candidateBlocksOrdered[i * finallyBlocksSize + j])
				}

				val comparatorState = getScopeForBlocks(finallyBlocksOrdered, candidateBlocksSubset)
				statesList.add(comparatorState)
			}
			states = statesList
		} else {
			val finallyStateFactory =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.UNMERGEABLE_STATE)
			val candidateStateFactory =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.UNMERGEABLE_STATE)
			val newState =
				TraverserActivePathState.produceFromFactories(comparator, finallyStateFactory, candidateStateFactory)
			states = listOf(newState)
		}
		return states
	}

	private fun duplicateForPaths(
		comparator: TraverserActivePathState,
		advancingState: UnknownAdvanceStrategyTraverserState,
		otherState: TraverserState,
		duplicateIsFromFinally: Boolean,
	): List<TraverserActivePathState> {
		val nextPredecessors: List<BlockNode> = advancingState.nextBlocks
		val newPaths = ArrayList<TraverserActivePathState>(nextPredecessors.size)
		for (predecessor in nextPredecessors) {
			val centralityState: CentralityState = advancingState.centralityState
			val duplicatePathBlockInfo = TraverserBlockInfo(predecessor)
			val duplicatePathStateFactory = NewBlockTraverserState.getFactory(centralityState, duplicatePathBlockInfo)
			val otherStateFactory: TraverserStateFactory<*> = DuplicatedTraverserStateFactory(otherState)

			val comparatorDuplicated = comparator.duplicate()
			val newPathState = if (duplicateIsFromFinally) {
				TraverserActivePathState.produceFromFactories(comparatorDuplicated, duplicatePathStateFactory, otherStateFactory)
			} else {
				TraverserActivePathState.produceFromFactories(comparatorDuplicated, otherStateFactory, duplicatePathStateFactory)
			}
			newPaths.add(newPathState)
		}
		return newPaths
	}

	private fun getScopeForBlocks(finallyBlocks: List<BlockNode>, candidateBlocks: List<BlockNode>): TraverserActivePathState {
		val comparator = comparator
		val mth: MethodNode = comparator.globalCommonState.methodNode

		val finallyState: TraverserState = comparator.getFinallyState()
		val candidateState: TraverserState = comparator.getCandidateState()

		val finallyGlobalState: GlobalTraverserSourceState = comparator.getGlobalStateFor(finallyState)
		val finallyCentralityState = finallyState.centralityState
		val finallyTerminator =
			BlockUtils.getBottomCommonPredecessor(mth, finallyBlocks, finallyGlobalState.containedBlocks)
		val finallyStateFactory = IdentifiedScopeWithTerminatorTraverserState.getFactory(
			finallyCentralityState,
			finallyBlocks,
			checkNotNull(finallyTerminator),
		)

		val candidateGlobalState: GlobalTraverserSourceState = comparator.getGlobalStateFor(candidateState)
		val candidateCentralityState = candidateState.centralityState
		val candidateTerminator =
			BlockUtils.getBottomCommonPredecessor(mth, candidateBlocks, candidateGlobalState.containedBlocks)
		val candidateStateFactory = IdentifiedScopeWithTerminatorTraverserState.getFactory(
			candidateCentralityState,
			candidateBlocks,
			checkNotNull(candidateTerminator),
		)

		return TraverserActivePathState.produceFromFactories(comparator, finallyStateFactory, candidateStateFactory)
	}
}
