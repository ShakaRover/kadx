package jadx.core.dex.visitors.finaly.traverser.handlers

import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.GlobalTraverserSourceState
import jadx.core.dex.visitors.finaly.traverser.TraverserController
import jadx.core.dex.visitors.finaly.traverser.TraverserException
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.state.IdentifiedScopeWithTerminatorTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.NewBlockTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.RecoveredFromCacheTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import jadx.core.dex.visitors.finaly.traverser.state.TraverserGlobalCommonState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.Stack

/**
 * “多路径合并”处理器：把已识别作用域内的多条根路径与 finally 侧一一配对合并。
 *
 * **背景**：当 try 出口作用域被识别后（[IdentifiedScopeWithTerminatorTraverserState]），
 * 该作用域可能有多个根块。本处理器会枚举候选侧根块的所有排列，逐条尝试搜索；
 * 找到一条“完美匹配”（或虽然不完美但终点已被评估过）的排列后，合并各路径的
 * 匹配结果（指令对、允许的输出寄存器、中心性开关），并生成下一步状态。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 的静态辅助方法全部放入 `companion object`，其中对外的
 *   [getAllPermutationsOfCollection]/[permutations] 加 `@JvmStatic` 保持 Java 静态调用；
 * - 原 Java 的对象引用比较（`==`）一律写作 `===`；
 * - 状态类 [PostMergeStatus] 只是普通可变值对象，保留为私有嵌套类。
 */
class MergePathActivePathTraverserHandler(comparatorState: TraverserActivePathState) : AbstractActivePathTraverserHandler(comparatorState) {

	/** 合并后汇总的状态：记录两侧是否允许中心、允许的输出寄存器集合、是否完美匹配。 */
	private class PostMergeStatus {
		val finallyAllowableOutputs: MutableSet<RegisterArg> = HashSet()
		val candidateAllowableOutputs: MutableSet<RegisterArg> = HashSet()
		var finallyAllowsCentral = false
		var candidateAllowsCentral = false
		var perfectMatch = true
	}

	@Throws(TraverserException::class)
	override fun handle(): List<TraverserActivePathState> {
		val comparator = comparator.duplicate()
		val commonState: TraverserGlobalCommonState = comparator.globalCommonState
		val finallyState = comparator.getFinallyState() as IdentifiedScopeWithTerminatorTraverserState
		val candidateState = comparator.getCandidateState() as IdentifiedScopeWithTerminatorTraverserState

		val finallyTerminus: BlockNode = finallyState.terminus
		val candidateTerminus: BlockNode = candidateState.terminus

		val abortFunction: (TraverserState) -> Boolean = getStateAbortOnTerminusFunction(finallyState, candidateState)

		val allPermutationsPaths: List<Array<BlockNode>> = getAllPermutationsOfCollection(candidateState.getRoots())
		var paths: List<TraverserActivePathState>? = null
		var postMerge: PostMergeStatus? = null
		for (candidateRootsPermutation in allPermutationsPaths) {
			val traversalPaths = ArrayList<TraverserActivePathState>()
			for (i in 0 until finallyState.getRoots().size) {
				val finallyRoot = finallyState.getRoots()[i]
				val candidateRoot = candidateRootsPermutation[i]

				val finallyCentrality = finallyState.centralityState.duplicate()
				val candidateCentrality = candidateState.centralityState.duplicate()

				val finallyBlockInfo = TraverserBlockInfo(finallyRoot)
				val candidateBlockInfo = TraverserBlockInfo(candidateRoot)

				val finallyStateFactory = NewBlockTraverserState.getFactory(finallyCentrality, finallyBlockInfo)
				val candidateStateFactory = NewBlockTraverserState.getFactory(candidateCentrality, candidateBlockInfo)

				val newState = TraverserActivePathState.produceFromFactories(comparator, finallyStateFactory, candidateStateFactory)
				traversalPaths.add(newState)
			}

			val currentPaths = ArrayList<TraverserActivePathState>()
			var errorOccurred = false
			for (pathState in traversalPaths) {
				val branchController = TraverserController(abortFunction)
				try {
					val out = branchController.process(pathState)
					currentPaths.addAll(out)
				} catch (e: TraverserException) {
					errorOccurred = true
					break
				}
			}

			if (errorOccurred) {
				// 发生错误说明该排列路径搜索失败，直接尝试下一个排列。
				continue
			}

			// 若此时 finally 终点与候选终点已被缓存，说明我们搜索的某条路径已经评估过这两个终点。
			// 此时即使不是“完美匹配”，只要路径还能从终点继续，就可以接受。
			val hasTerminusBeenEvaluatedInPaths = commonState.hasBlocksBeenCached(finallyTerminus, candidateTerminus)
			val currentPostMerge = getScopeSplitPostMergeStatus(currentPaths)
			if (!currentPostMerge.perfectMatch && !hasTerminusBeenEvaluatedInPaths) {
				// 不匹配
				continue
			}
			paths = currentPaths
			postMerge = currentPostMerge
			break
		}
		val validPaths = paths
		val validPostMerge = postMerge
		if (validPaths == null || validPostMerge == null) {
			val nonMatchingState = createNonMatchingTerminator(comparator)
			return listOf(nonMatchingState)
		}
		val newFinallyCentralityState = finallyState.centralityState.duplicate()
		newFinallyCentralityState.allowsCentral = validPostMerge.finallyAllowsCentral
		newFinallyCentralityState.addAllowableOutputs(validPostMerge.finallyAllowableOutputs)
		val newCandidateCentralityState = candidateState.centralityState.duplicate()
		newCandidateCentralityState.allowsCentral = validPostMerge.candidateAllowsCentral
		newCandidateCentralityState.addAllowableOutputs(validPostMerge.candidateAllowableOutputs)

		val finallyTerminusBlockInfo = TraverserBlockInfo(finallyState.terminus)
		val candidateTerminusBlockInfo = TraverserBlockInfo(candidateState.terminus)

		val finallyStateFactory = NewBlockTraverserState.getFactory(newFinallyCentralityState, finallyTerminusBlockInfo)
		val candidateStateFactory = NewBlockTraverserState.getFactory(newCandidateCentralityState, candidateTerminusBlockInfo)

		val nextState = TraverserActivePathState.produceFromFactories(comparator, finallyStateFactory, candidateStateFactory)
		nextState.mergeWith(validPaths)
		return listOf(nextState)
	}

	companion object {
		/** 产生“路径不匹配”的终止状态。 */
		private fun createNonMatchingTerminator(state: TraverserActivePathState): TraverserActivePathState {
			val finallyStateFactory =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.NON_MATCHING_PATHS)
			val candidateStateFactory =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.NON_MATCHING_PATHS)

			return TraverserActivePathState.produceFromFactories(state, finallyStateFactory, candidateStateFactory)
		}

		/** 判断某状态当前是否停在给定终点块上（块按引用比较）。 */
		private fun isStateOnTerminus(state: TraverserState, terminus: BlockNode): Boolean {
			val blockInfo = state.getBlockInsnInfo()
			if (blockInfo == null) {
				return false
			}
			return blockInfo.block === terminus
		}

		/**
		 * 构造“到达终点即中止”的判定函数：
		 * 根据状态属于 finally 子图还是候选子图，分别与其终点比较。
		 */
		private fun getStateAbortOnTerminusFunction(
			finallyState: IdentifiedScopeWithTerminatorTraverserState,
			candidateState: IdentifiedScopeWithTerminatorTraverserState,
		): (TraverserState) -> Boolean {
			val finallyTerminus: BlockNode = finallyState.terminus
			val candidateTerminus: BlockNode = candidateState.terminus
			val finallyGlobalState: GlobalTraverserSourceState = finallyState.globalState
			val candidateGlobalState: GlobalTraverserSourceState = candidateState.globalState

			return { state ->
				if (state.globalState === finallyGlobalState) {
					isStateOnTerminus(state, finallyTerminus)
				} else if (state.globalState === candidateGlobalState) {
					isStateOnTerminus(state, candidateTerminus)
				} else {
					throw JadxRuntimeException("Unknown global traverser state. Has a global state been duplicated?")
				}
			}
		}

		/**
		 * 汇总一批路径的合并状态。
		 *
		 * 若作用域分裂方式相同，则所有分支都不能以终止符结束；只有两侧都“从缓存恢复”
		 * 且都不能继续时，才取缓存底层状态继续汇总。
		 */
		private fun getScopeSplitPostMergeStatus(pathsTaken: List<TraverserActivePathState>): PostMergeStatus {
			// 若作用域分裂方式相同，则所有分支都不能以终止符结束。
			val status = PostMergeStatus()
			for (path in pathsTaken) {
				val finallyState: TraverserState
				val candidateState: TraverserState
				if (path.getFinallyState().isTerminal() || path.getCandidateState().isTerminal()) {
					val rawFinallyState: TraverserState = path.getFinallyState()
					val rawCandidateState: TraverserState = path.getCandidateState()
					val finallyIsCached = rawFinallyState is RecoveredFromCacheTraverserState
					val candidateIsCached = rawCandidateState is RecoveredFromCacheTraverserState
					if (!(finallyIsCached && candidateIsCached)) {
						status.perfectMatch = false
						continue
					}

					val finallyCachedState = rawFinallyState as RecoveredFromCacheTraverserState
					val candidateCachedState = rawCandidateState as RecoveredFromCacheTraverserState
					if (finallyCachedState.canContinue() || candidateCachedState.canContinue()) {
						status.perfectMatch = false
						continue
					}
					finallyState = finallyCachedState.getUnderlying()
					candidateState = candidateCachedState.getUnderlying()
				} else {
					finallyState = path.getFinallyState()
					candidateState = path.getCandidateState()
				}
				val finallyCentralityState = finallyState.centralityState
				val candidateCentralityState = candidateState.centralityState
				status.finallyAllowsCentral = status.finallyAllowsCentral && finallyCentralityState.allowsCentral
				status.candidateAllowsCentral = status.candidateAllowsCentral && candidateCentralityState.allowsCentral
				status.finallyAllowableOutputs.addAll(finallyCentralityState.allowableOutputArguments)
				status.candidateAllowableOutputs.addAll(candidateCentralityState.allowableOutputArguments)
			}
			return status
		}

		/**
		 * 枚举给定集合中所有元素的排列。
		 *
		 * 内部先复制一份可变集合，避免修改调用方传入的只读列表（原 Java 会在结束时还原，
		 * 这里用副本达到相同的净效果）。
		 */
		fun getAllPermutationsOfCollection(elements: Collection<BlockNode>): List<Array<BlockNode>> {
			val mutableElements: MutableCollection<BlockNode> = ArrayList(elements)
			val permutationStack = Stack<BlockNode>()
			val permutationsResult = ArrayList<Array<BlockNode>>()
			permutations(permutationsResult, mutableElements, permutationStack, mutableElements.size)
			return permutationsResult
		}

		/** 递归生成排列：逐个选择剩余元素入栈，递归后回溯。 */
		fun permutations(
			permutationsResult: MutableList<Array<BlockNode>>,
			elements: MutableCollection<BlockNode>,
			permutationStack: Stack<BlockNode>,
			size: Int,
		) {
			if (permutationStack.size == size) {
				permutationsResult.add(permutationStack.toTypedArray())
			}
			val availableItems = elements.toTypedArray()
			for (i in availableItems) {
				permutationStack.push(i)
				elements.remove(i)
				permutations(permutationsResult, elements, permutationStack, size)
				elements.add(permutationStack.pop())
			}
		}
	}
}
