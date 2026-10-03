package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockPathTraverserHandler

/**
 * 终止状态：遍历到此结束，并记录终止原因 [terminationReason]。
 *
 * **Kotlin 转换说明**：
 * - [TerminationReason] 为嵌套枚举，Java 仍可写
 *   `TerminalTraverserState.TerminationReason.END_OF_PATH`；
 * - `getFactory` 供 Java 侧静态调用（`@JvmStatic`）。
 */
class TerminalTraverserState(
	state: TraverserActivePathState,
	private val terminationReason: TerminationReason,
) : TraverserState(state) {

	/**
	 * 遍历终止的原因。
	 */
	enum class TerminationReason {
		/**
		 * 在 finally 块与候选块之间比较指令时发现不匹配指令，要求终止遍历。
		 */
		NON_MATCHING_INSTRUCTIONS,

		NON_MATCHING_PATHS,

		/**
		 * 处理器请求查找某个块的前驱时，作用域内已不存在前驱。
		 */
		END_OF_PATH,

		/**
		 * 处理器请求处理某个块时，该处理器的缓存结果已经存在。
		 */
		USING_CACHED_RESULTS,

		UNMERGEABLE_STATE,

		UNRESOLVABLE_STATES,
	}

	override fun isTerminal(): Boolean = true

	override fun getNextHandler(): AbstractBlockPathTraverserHandler? = null

	fun getTerminationReason(): TerminationReason = terminationReason

	override fun getCompareState(): ComparisonState = ComparisonState.NOT_READY

	override fun getUnderlyingCentralityState(): CentralityState? = null

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo? = null

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState = TerminalTraverserState(comparatorState, terminationReason)

	companion object {
		fun getFactory(terminationReason: TerminationReason): TraverserStateFactory<TerminalTraverserState> = TerminalStateFactory(terminationReason)
	}

	private class TerminalStateFactory(private val terminationReason: TerminationReason) : TraverserStateFactory<TerminalTraverserState>() {

		override fun generateInternalState(state: TraverserActivePathState): TerminalTraverserState = TerminalTraverserState(state, terminationReason)
	}
}
