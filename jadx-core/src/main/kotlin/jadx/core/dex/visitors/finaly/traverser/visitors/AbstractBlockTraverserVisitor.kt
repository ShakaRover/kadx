package jadx.core.dex.visitors.finaly.traverser.visitors

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState
import jadx.core.dex.visitors.finaly.traverser.state.TraverserState

/**
 * “块访问器”的抽象基类。
 *
 * **作用**：处理器把对单个块的处理委托给访问器。访问器持有当前遍历状态，
 * 并暴露比较用的活动路径状态（[getComparator]）。
 *
 * **Kotlin 转换说明**：原 Java 的 [getState]/[getComparator] 是非 final 方法，
 * 这里保留 `open`，Java/Kotlin 子类仍可覆写；[visit] 保持抽象。
 */
abstract class AbstractBlockTraverserVisitor(private val state: TraverserState) {

	/** 访问一个块，返回处理后的遍历状态。 */
	abstract fun visit(block: BlockNode): TraverserState

	/** 当前遍历状态。 */
	open fun getState(): TraverserState = state

	/** 当前活动路径状态（比较上下文）。 */
	open fun getComparator(): TraverserActivePathState = state.getComparatorState()
}
