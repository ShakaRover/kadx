package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.visitors.finaly.CentralityState
import jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory
import jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockPathTraverserHandler
import jadx.core.dex.visitors.finaly.traverser.handlers.BaseBlockTraverserHandler

/**
 * “进入了一个新块”的状态：此时对该块还没有任何游标信息，
 * 需要先交给 [BaseBlockTraverserHandler] 收集块内指令信息（隐式指令、路径结尾指令等）。
 *
 * **Kotlin 转换说明**：`getFactory` 被 Java 侧（C14 处理器）静态调用，
 * 放入 `companion object` 并加 `@JvmStatic`；内部工厂类保持私有。
 */
class NewBlockTraverserState(
	state: TraverserActivePathState,
	private val centralityStateValue: CentralityState,
	private val blockInsnInfo: TraverserBlockInfo,
) : TraverserState(state) {

	override fun getCompareState(): ComparisonState = ComparisonState.NOT_READY

	override fun isTerminal(): Boolean = false

	override fun getNextHandler(): AbstractBlockPathTraverserHandler {
		// 还没有该块的信息，先用基础处理器收集信息，再产生后续状态。
		return BaseBlockTraverserHandler(this)
	}

	override fun getUnderlyingCentralityState(): CentralityState = centralityStateValue

	override fun getUnderlyingBlockInsnInfo(): TraverserBlockInfo = blockInsnInfo

	override fun duplicateInternalState(comparatorState: TraverserActivePathState): TraverserState {
		val dCentralityState = centralityStateValue.duplicate()
		val dBlockInsnInfo = blockInsnInfo.duplicate()
		return NewBlockTraverserState(comparatorState, dCentralityState, dBlockInsnInfo)
	}

	companion object {
		fun getFactory(
			centralityState: CentralityState,
			blockInsnInfo: TraverserBlockInfo,
		): TraverserStateFactory<NewBlockTraverserState> = NewBlockStateFactory(centralityState, blockInsnInfo)
	}

	private class NewBlockStateFactory(
		private val centralityState: CentralityState,
		private val blockInsnInfo: TraverserBlockInfo,
	) : TraverserStateFactory<NewBlockTraverserState>() {

		override fun generateInternalState(state: TraverserActivePathState): NewBlockTraverserState = NewBlockTraverserState(state, centralityState, blockInsnInfo)
	}
}
