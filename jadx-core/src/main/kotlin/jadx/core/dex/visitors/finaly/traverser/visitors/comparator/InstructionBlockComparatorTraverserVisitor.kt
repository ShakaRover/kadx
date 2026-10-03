package jadx.core.dex.visitors.finaly.traverser.visitors.comparator

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.SameInstructionsStrategy
import jadx.core.dex.visitors.finaly.SameInstructionsStrategyImpl
import jadx.core.dex.visitors.finaly.traverser.factory.DuplicatedTraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.state.NoBlockTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.utils.Pair

/**
 * 指令块比较访问器：这是 finally 重复指令识别的核心比较器。
 *
 * **算法**：从块尾向前逐条比较 finally 块与候选块的指令：
 * - 全部匹配且两侧长度相同：产生“完美匹配”状态，继续比较下一对块；
 * - 全部匹配但长度不同（一侧多出指令）：把多出的一侧标记为可跳过，并调整游标；
 * - 一条都不匹配但某侧允许跳过非起始节点：尝试把该侧整块跳过；
 * - 其他情况：产生“指令不匹配”终止状态。
 *
 * **Kotlin 转换说明**：4 个原 Java 静态辅助方法放入 `companion object`（私有）；
 * 指令/块一律按引用比较（`===`）；集合类型使用 Kotlin 侧类型。
 */
class InstructionBlockComparatorTraverserVisitor : AbstractTraverserComparatorVisitor() {

	private val sameInstructionsStrategy: SameInstructionsStrategy = SameInstructionsStrategyImpl()

	override fun visit(state: TraverserActivePathState): TraverserActivePathState {
		val finallyState: TraverserState = state.getFinallyState()
		val candidateState: TraverserState = state.getCandidateState()

		val finallyBlockInfo = finallyState.getBlockInsnInfo()
		val candidateBlockInfo = candidateState.getBlockInsnInfo()

		if (finallyBlockInfo == null || candidateBlockInfo == null) {
			throw UnsupportedOperationException(
				"The instruction comparator handler has received a state which does not support block insn info",
			)
		}

		val finallyBlock: BlockNode = finallyBlockInfo.block
		val candidateBlock: BlockNode = candidateBlockInfo.block

		val finallyInsns: List<InsnNode> = finallyBlockInfo.insnsSlice
		val candidateInsns: List<InsnNode> = candidateBlockInfo.insnsSlice
		val finallyInsnsSize = finallyInsns.size
		val candidateInsnsSize = candidateInsns.size

		val maxIterateCount = minOf(finallyInsnsSize, candidateInsnsSize)

		val matchingInsns = ArrayList<Pair<InsnNode>>(maxIterateCount)

		// 从块尾向前逐条比较，统计连续匹配的指令数量（一旦不匹配立即停止）。
		for (i in 0 until maxIterateCount) {
			val candidateInsn = candidateInsns[candidateInsnsSize - i - 1]
			val finallyInsn = finallyInsns[finallyInsnsSize - i - 1]

			if (!sameInstructionsStrategy.sameInsns(candidateInsn, finallyInsn)) {
				break
			}

			val match = Pair(finallyInsn, candidateInsn)
			matchingInsns.add(match)
		}

		val matchedInsnsCount = matchingInsns.size

		// 登记两侧块的匹配进度（哪些下标的指令已被消费）。
		state.registerWithBlockInfo(finallyBlockInfo, matchedInsnsCount)
		state.registerWithBlockInfo(candidateBlockInfo, matchedInsnsCount)

		val finallyOverruns = finallyInsnsSize > candidateInsnsSize
		val candidateOverruns = finallyInsnsSize < candidateInsnsSize
		val sameSizedSlices = !finallyOverruns && !candidateOverruns
		val allMatched = matchedInsnsCount == maxIterateCount
		val noneMatched = matchedInsnsCount == 0

		state.matchedInsns.addAll(matchingInsns)

		val newState: TraverserActivePathState
		if (allMatched) {
			if (sameSizedSlices) {
				// 全部匹配且两侧长度相同：继续比较下一对块。
				newState = createStateForPerfectMatch(state, finallyBlock, candidateBlock)
			} else {
				// 全部匹配但一侧更长：让指令已搜完的一侧先进入下一对块。
				newState = createStateForUnevenMatch(
					state,
					finallyState,
					candidateState,
					finallyBlock,
					candidateBlock,
					finallyInsnsSize,
					candidateInsnsSize,
				)
			}
		} else if (noneMatched && eitherStateAllowsBlockSkip(finallyState, candidateState)) {
			newState = createStateForBlockSkip(state, finallyState, candidateState, finallyBlock, candidateBlock)
		} else {
			// 块尾首条指令就不匹配：说明后续块不应再被标记为重复，返回终止状态停止搜索。
			newState = createStateForTerminatorState(state)
		}

		return newState
	}

	private fun eitherStateAllowsBlockSkip(finallyState: TraverserState, candidateState: TraverserState): Boolean {
		val finallyCentralityState = finallyState.centralityState
		val candidateCentralityState = candidateState.centralityState

		return finallyCentralityState.allowsNonStartingNode || candidateCentralityState.allowsNonStartingNode
	}

	companion object {
		/**
		 * 两侧指令全部匹配且块长度相同：
		 * 关闭“允许中心/允许非起始节点”开关，并把两侧都转为 [NoBlockTraverserState]，
		 * 表示本块已比较完，继续处理下一对块。
		 */
		private fun createStateForPerfectMatch(
			previousState: TraverserActivePathState,
			finallyBlock: BlockNode,
			candidateBlock: BlockNode,
		): TraverserActivePathState {
			val finallyCentralityState = previousState.getFinallyState().centralityState.duplicate()
			val candidateCentralityState = previousState.getCandidateState().centralityState.duplicate()

			finallyCentralityState.allowsCentral = false
			candidateCentralityState.allowsCentral = false
			finallyCentralityState.allowsNonStartingNode = false
			candidateCentralityState.allowsNonStartingNode = false

			val finallyStateProducer = NoBlockTraverserState.getFactory(finallyCentralityState, finallyBlock)
			val candidateStateProducer = NoBlockTraverserState.getFactory(candidateCentralityState, candidateBlock)

			return TraverserActivePathState.produceFromFactories(previousState, finallyStateProducer, candidateStateProducer)
		}

		/**
		 * 两侧指令全部匹配，但其中一侧块更长（还有指令未比较）：
		 * 把较长的一侧用“复制状态工厂”保留下来，较短的一侧转为 [NoBlockTraverserState]，
		 * 并调整游标跳过已匹配的指令数。
		 */
		private fun createStateForUnevenMatch(
			previousState: TraverserActivePathState,
			finallyState: TraverserState,
			candidateState: TraverserState,
			finallyBlock: BlockNode,
			candidateBlock: BlockNode,
			finallyInsnsSize: Int,
			candidateInsnsSize: Int,
		): TraverserActivePathState {
			val maxIterateCount = maxOf(finallyInsnsSize, candidateInsnsSize)
			val finallyOverruns = finallyInsnsSize > candidateInsnsSize

			val insnsDelta: Int
			val newFinallyStateProducer: TraverserStateFactory<*>
			val newCandidateStateProducer: TraverserStateFactory<*>
			val adjustedBlockInfo: TraverserBlockInfo
			if (finallyOverruns) {
				// finally 侧指令比候选侧多
				val candidateCentralityState = candidateState.centralityState.duplicate()
				candidateCentralityState.allowsCentral = false
				candidateCentralityState.allowsNonStartingNode = false
				val finallyCentralityState = finallyState.centralityState
				finallyCentralityState.allowsCentral = false
				finallyCentralityState.allowsNonStartingNode = false

				insnsDelta = finallyInsnsSize - maxIterateCount
				newFinallyStateProducer = DuplicatedTraverserStateFactory(finallyState)
				adjustedBlockInfo = checkNotNull(finallyState.getBlockInsnInfo())
				newCandidateStateProducer = NoBlockTraverserState.getFactory(candidateCentralityState, candidateBlock)
			} else {
				// 候选侧指令比 finally 侧多
				val finallyCentralityState = finallyState.centralityState.duplicate()
				finallyCentralityState.allowsCentral = false
				finallyCentralityState.allowsNonStartingNode = false
				val candidateCentralityState = candidateState.centralityState
				candidateCentralityState.allowsCentral = false
				candidateCentralityState.allowsNonStartingNode = false

				insnsDelta = candidateInsnsSize - maxIterateCount
				candidateState.centralityState.allowsCentral = false
				newCandidateStateProducer = DuplicatedTraverserStateFactory(candidateState)
				adjustedBlockInfo = checkNotNull(candidateState.getBlockInsnInfo())
				newFinallyStateProducer = NoBlockTraverserState.getFactory(finallyCentralityState, finallyBlock)
			}
			adjustedBlockInfo.bottomOffset = adjustedBlockInfo.bottomOffset + insnsDelta

			return TraverserActivePathState.produceFromFactories(previousState, newFinallyStateProducer, newCandidateStateProducer)
		}

		/**
		 * 一条指令都没匹配，但某一侧允许跳过“非起始节点”：
		 * 优先修复 finally 侧（把它转为 [NoBlockTraverserState] 并关闭开关），
		 * 否则修复候选侧。另一侧保持原状态（复制到新路径）。
		 */
		private fun createStateForBlockSkip(
			previousState: TraverserActivePathState,
			finallyState: TraverserState,
			candidateState: TraverserState,
			finallyBlock: BlockNode,
			candidateBlock: BlockNode,
		): TraverserActivePathState {
			val finallyCentralityState = finallyState.centralityState
			val candidateCentralityState = candidateState.centralityState

			// 先尝试修复 finally 路径；若后续仍失败，再在后续迭代中尝试修复候选路径。
			if (finallyCentralityState.allowsNonStartingNode) {
				finallyCentralityState.allowsNonStartingNode = false
				val newFinallyStateProducer = NoBlockTraverserState.getFactory(finallyCentralityState, finallyBlock)
				val newCandidateStateProducer: TraverserStateFactory<*> = DuplicatedTraverserStateFactory(candidateState)
				return TraverserActivePathState.produceFromFactories(previousState, newFinallyStateProducer, newCandidateStateProducer)
			} else {
				candidateCentralityState.allowsNonStartingNode = false
				val newCandidateStateProducer = NoBlockTraverserState.getFactory(candidateCentralityState, candidateBlock)
				val newFinallyStateProducer: TraverserStateFactory<*> = DuplicatedTraverserStateFactory(finallyState)
				return TraverserActivePathState.produceFromFactories(previousState, newFinallyStateProducer, newCandidateStateProducer)
			}
		}

		/** 产生“指令不匹配”的终止状态，停止本路径的搜索。 */
		private fun createStateForTerminatorState(previousState: TraverserActivePathState): TraverserActivePathState {
			val finallyStateProducer =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.NON_MATCHING_INSTRUCTIONS)
			val candidateStateProducer =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.NON_MATCHING_INSTRUCTIONS)

			return TraverserActivePathState.produceFromFactories(previousState, finallyStateProducer, candidateStateProducer)
		}
	}
}
