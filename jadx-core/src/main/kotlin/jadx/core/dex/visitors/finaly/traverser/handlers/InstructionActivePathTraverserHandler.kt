package jadx.core.dex.visitors.finaly.traverser.handlers

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.traverser.TraverserException
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserGlobalCommonState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState
import jadx.core.dex.visitors.finaly.traverser.visitors.comparator.InstructionBlockComparatorTraverserVisitor

/**
 * 指令比较处理器：对“finally 块”与“候选块”逐条比较块尾指令。
 *
 * **职责**：调用 [InstructionBlockComparatorTraverserVisitor] 完成实际比较，并把比较结果
 * 缓存到 [TraverserGlobalCommonState]，避免同一对块被重复搜索。
 *
 * **Kotlin 转换说明**：
 * - 内部类 [UnresolvableBlockException] 是原 Java 的 `public static final class`，Kotlin 嵌套类
 *   默认就是 static，JVM 名称不变；
 * - [TraverserActivePathState.getBlockInsnInfo] 在 C13 迁移后返回可空类型，这里沿用 C13
 *   [jadx.core.dex.visitors.finaly.traverser.TraverserController] 的写法 `?.block`。
 */
class InstructionActivePathTraverserHandler(state: TraverserActivePathState) : AbstractActivePathTraverserHandler(state) {

	/** 当某块无法进行指令比较时抛出，携带块信息与原因。 */
	class UnresolvableBlockException(block: BlockNode, reason: String) : TraverserException("A block, " + block.toString() + ", could not have instructions compared.\n\t" + reason)

	@Throws(TraverserException::class)
	override fun handle(): List<TraverserActivePathState> {
		val comparator: TraverserActivePathState = getComparator()
		val commonState: TraverserGlobalCommonState = comparator.getGlobalCommonState()

		val finallyState: TraverserState = comparator.getFinallyState()
		val candidateState: TraverserState = comparator.getCandidateState()

		val finallyBlockInfo = finallyState.getBlockInsnInfo()
		val candidateBlockInfo = candidateState.getBlockInsnInfo()
		val finallyBlock: BlockNode? = finallyBlockInfo?.block
		val candidateBlock: BlockNode? = candidateBlockInfo?.block

		val visitor = InstructionBlockComparatorTraverserVisitor()
		val newState: TraverserActivePathState = visitor.visit(comparator)

		if (finallyBlock != null && candidateBlock != null) {
			commonState.addCachedStateFor(finallyBlock, candidateBlock, listOf(newState))
		}
		return listOf(newState)
	}
}
