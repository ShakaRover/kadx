package jadx.core.dex.visitors.finaly.traverser

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractActivePathTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockPathTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.state.RecoveredFromCacheTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo
import jadx.core.dex.visitors.finaly.traverser.state.TraverserGlobalCommonState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

/**
 * 遍历器控制器：负责驱动两侧（finally / candidate）状态机向前推进，直到完成比较或终止。
 *
 * **术语**：
 * - “finally”子图：作为 finally 候选的那一份代码；
 * - “candidate”子图：被拿来与 finally 子图比较、看是否重复的另一份代码。
 *
 * 控制器不断调用 [advance] 推进状态；遇到分叉时递归地派生新的控制器分别处理；
 * 直到两个状态都不再变化（无法解析）或某一侧终止。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 方法声明 `throws TraverserException`（受检异常），Java 调用方（C14 处理器）
 *   需要 `catch (TraverserException)`，因此这里用 `@Throws` 保留字节码中的 throws 声明；
 * - 原 Java 的 `==` 状态比较为引用比较，一律写作 `===`。
 */
class TraverserController {

	private val stateAbortCondition: Function<TraverserState, Boolean>?

	constructor() {
		this.stateAbortCondition = null
	}

	constructor(stateAbortCondition: Function<TraverserState, Boolean>?) {
		this.stateAbortCondition = stateAbortCondition
	}

	/**
	 * 从给定的活动路径状态出发，持续推演整条路径。
	 *
	 * 终止条件：
	 * - 两侧状态都满足中止条件；
	 * - 任一侧状态已终止；
	 * - 开始比较一对已经比较过的块；
	 * - 两次 [advance] 之间状态没有变化（无法解析）。
	 *
	 * 返回所有在各自分支终止点上的路径状态。
	 */
	@Throws(TraverserException::class)
	fun process(state: TraverserActivePathState): List<TraverserActivePathState> {
		var nextState = state
		val previousFinallyState = AtomicReference<TraverserState?>(null)
		val previousCandidateState = AtomicReference<TraverserState?>(null)
		while (true) {
			val advancedStates = advance(nextState, previousFinallyState, previousCandidateState)
			if (advancedStates == null || advancedStates.isEmpty()) {
				break
			}
			if (advancedStates.size != 1) {
				val nextController = TraverserController(stateAbortCondition)
				val returnStates = ArrayList<TraverserActivePathState>()
				for (advancedState in advancedStates) {
					val childStates = nextController.process(advancedState)
					returnStates.addAll(childStates)
				}
				return returnStates
			}
			nextState = advancedStates[0]
		}
		return listOf(nextState)
	}

	/**
	 * 单步推进：比较两侧状态，必要时执行处理器或生成终止状态。
	 *
	 * 返回 null 表示不再继续（例如任一侧已终止）。
	 */
	@Throws(TraverserException::class)
	fun advance(
		state: TraverserActivePathState,
		previousFinallyState: AtomicReference<TraverserState?>,
		previousCandidateState: AtomicReference<TraverserState?>,
	): List<TraverserActivePathState>? {
		val commonState = state.getGlobalCommonState()
		val finallyState = state.getFinallyState()
		val candidateState = state.getCandidateState()

		if (previousFinallyState.get() === finallyState && previousCandidateState.get() === candidateState) {
			// 两侧状态都没变，说明无法再解析，直接标记为终止。
			val finallyStateProducer =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.UNRESOLVABLE_STATES)
			val candidateStateProducer =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.UNRESOLVABLE_STATES)
			return listOf(TraverserActivePathState.produceFromFactories(state, finallyStateProducer, candidateStateProducer))
		}
		previousFinallyState.set(finallyState)
		previousCandidateState.set(candidateState)
		if (finallyState.isTerminal() || candidateState.isTerminal()) {
			return null
		}
		if (finallyState.javaClass === candidateState.javaClass &&
			finallyState.getCompareState() == TraverserState.ComparisonState.READY_TO_COMPARE &&
			candidateState.getCompareState() == TraverserState.ComparisonState.READY_TO_COMPARE
		) {
			val finallyBlockInfo = finallyState.getBlockInsnInfo()
			val candidateBlockInfo = candidateState.getBlockInsnInfo()
			val finallyBlock: BlockNode? = finallyBlockInfo?.block
			val candidateBlock: BlockNode? = candidateBlockInfo?.block
			if (finallyBlock != null && candidateBlock != null &&
				commonState.hasBlocksBeenCached(finallyBlock, candidateBlock)
			) {
				val dupStates = checkNotNull(commonState.getCachedStateFor(finallyBlock, candidateBlock))
				val recoveredFromCacheStates = ArrayList<TraverserActivePathState>(dupStates.size)
				for (dupState in dupStates) {
					val reusedFinallyState = dupState.getFinallyState()
					val reusedCandidateState = dupState.getCandidateState()
					val finallyStateProducer = RecoveredFromCacheTraverserState.getFactory(reusedFinallyState)
					val candidateStateProducer = RecoveredFromCacheTraverserState.getFactory(reusedCandidateState)
					val recoveredFromCacheState =
						TraverserActivePathState.produceFromFactories(state, finallyStateProducer, candidateStateProducer)
					recoveredFromCacheState.mergeWith(dupStates)
					recoveredFromCacheStates.add(recoveredFromCacheState)
				}
				return recoveredFromCacheStates
			}
			val handler = candidateState.getNextHandler()
			return processHandlerImplementations(state, handler)
		}
		val hasReadyToCompare = finallyState.getCompareState() == TraverserState.ComparisonState.READY_TO_COMPARE ||
			candidateState.getCompareState() == TraverserState.ComparisonState.READY_TO_COMPARE
		val finallyStateAborted = advanceSingleState(state, finallyState, hasReadyToCompare)
		val candidateStateAborted = advanceSingleState(state, candidateState, hasReadyToCompare)
		if (finallyStateAborted && candidateStateAborted) {
			return null
		}
		return listOf(state)
	}

	/**
	 * 推进单个状态一次。
	 *
	 * @return 该状态是否被中止条件中止。
	 */
	@Throws(TraverserException::class)
	private fun advanceSingleState(
		activePathState: TraverserActivePathState,
		singleState: TraverserState,
		hasReadyToCompare: Boolean,
	): Boolean {
		val stateAborted = if (stateAbortCondition != null) stateAbortCondition.apply(singleState) else false
		if (stateAbortCondition == null || !stateAborted) {
			if (singleState.getCompareState() == TraverserState.ComparisonState.NOT_READY ||
				(
					singleState.getCompareState() == TraverserState.ComparisonState.AWAITING_OPTIONAL_PREDECESSOR_MERGE &&
						hasReadyToCompare
					)
			) {
				val handler = singleState.getNextHandler()
				val results = processHandlerImplementations(activePathState, handler)
				if (results.size != 1 || results[0] !== activePathState) {
					throw JadxRuntimeException("A traverser handler which was not expected to change path states actually did")
				}
			}
		}
		return stateAborted
	}

	companion object {
		/**
		 * 根据处理器类型分发执行：块路径处理器原地处理并返回同一状态；
		 * 活动路径处理器可能返回多条派生路径。
		 */
		@Throws(TraverserException::class)
		private fun processHandlerImplementations(
			state: TraverserActivePathState,
			handler: AbstractBlockTraverserHandler?,
		): List<TraverserActivePathState> {
			if (handler is AbstractBlockPathTraverserHandler) {
				handler.process()
				return listOf(state)
			} else if (handler is AbstractActivePathTraverserHandler) {
				return handler.process()
			} else {
				throw JadxRuntimeException(
					"A sealed class, " + AbstractBlockPathTraverserHandler::class.java.simpleName +
						", has an unknown implementation",
				)
			}
		}
	}
}
