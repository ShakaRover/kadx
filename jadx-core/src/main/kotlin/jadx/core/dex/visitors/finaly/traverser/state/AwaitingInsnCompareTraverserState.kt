package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.InstructionActivePathTraverserHandler

/**
 * “等待指令比较”的状态：块内游标已就绪，下一步应逐条比较两侧的指令。
 *
 * **Kotlin 转换说明**：`getNextHandler()` 返回 Java 侧的
 * [InstructionActivePathTraverserHandler]，它负责真正执行指令匹配。
 */
class AwaitingInsnCompareTraverserState(
	state: TraverserActivePathState,
	private val centralityStateValue: CentralityState,
	private val blockInsnInfo: TraverserBlockInfo,
) : TraverserState(state) {

	override fun getNextHandler(): AbstractBlockTraverserHandler = InstructionActivePathTraverserHandler(comparatorState)

	override fun getCompareState(): ComparisonState = ComparisonState.READY_TO_COMPARE

	override fun isTerminal(): Boolean = false

	override fun getUnderlyingCentralityState(): CentralityState = centralityStateValue

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo = blockInsnInfo

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState {
		val dCentralityState = centralityStateValue.duplicate()
		val dBlockInsnInfo = blockInsnInfo.duplicate()
		return AwaitingInsnCompareTraverserState(comparatorState, dCentralityState, dBlockInsnInfo)
	}
}
