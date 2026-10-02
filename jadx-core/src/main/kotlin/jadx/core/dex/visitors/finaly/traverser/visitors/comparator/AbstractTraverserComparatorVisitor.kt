package jadx.core.dex.visitors.finaly.traverser.visitors.comparator

import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState

/**
 * “比较访问器”的抽象基类。
 *
 * **作用**：对一条活动路径状态执行比较逻辑（例如逐条比较 finally 块与候选块的指令），
 * 并返回推进后的新状态。
 *
 * **Kotlin 转换说明**：原 Java 只有抽象方法 [visit]，此处原样保留为抽象类，
 * 保证 [InstructionBlockComparatorTraverserVisitor] 的继承关系不变。
 */
abstract class AbstractTraverserComparatorVisitor {

	/** 比较给定的活动路径状态，返回推进后的状态。 */
	abstract fun visit(state: TraverserActivePathState): TraverserActivePathState
}
