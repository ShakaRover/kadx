package jadx.core.dex.visitors.finaly.traverser.state

import jadx.core.dex.nodes.BlockNode

/**
 * 标记“持有源块”的遍历状态。
 *
 * **用途**：当某个块已经没有待比较指令时，需要沿前驱继续搜索；
 * 此时必须知道是从哪个块出发的（[getSourceBlock]），因此用该接口暴露源块。
 *
 * **Kotlin 转换说明**：保持为普通接口，Java 侧
 * `PredecessorBlockPathTraverserHandler<T extends TraverserState & ISourceBlockState>`
 * 仍可将其作为交叉类型上界使用。
 */
interface ISourceBlockState {

	fun getSourceBlock(): BlockNode
}
