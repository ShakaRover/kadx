package jadx.core.dex.visitors.finaly.traverser.handlers

import jadx.core.dex.visitors.finaly.traverser.TraverserException
import jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState

/**
 * “活动路径”处理器的抽象基类。
 *
 * **与块路径处理器的区别**：块路径处理器原地推进单个状态；活动路径处理器可能会
 * **分叉**出多条新的活动路径（例如前驱合并、作用域合并），因此 [process] 返回
 * 一组新的 [TraverserActivePathState]。
 *
 * **Kotlin 转换说明**：原 Java 的 `getComparator()` 被 Java 子类频繁调用，
 * 这里保留显式方法名，避免与属性合成 getter 产生歧义。
 */
abstract class AbstractActivePathTraverserHandler(private val comparatorState: TraverserActivePathState) : AbstractBlockTraverserHandler() {

	/** 子类实现：根据当前比较状态，产生一条或多条后续路径状态。 */
	@Throws(TraverserException::class)
	protected abstract fun handle(): List<TraverserActivePathState>

	/** 对外统一入口：执行一次处理并返回新路径。 */
	@Throws(TraverserException::class)
	fun process(): List<TraverserActivePathState> = handle()

	/** 返回本处理器所基于的活动路径状态。 */
	fun getComparator(): TraverserActivePathState = comparatorState
}
