package kadx.core.dex.visitors.finaly.traverser

import kadx.core.dex.nodes.BlockNode

/**
 * 全局（整条路径共享）的源块状态。
 *
 * **含义**：一次 finally 匹配中，finally 子图与候选子图各自拥有一个“包含块集合”，
 * 该集合在整条路径上保持不变（因此叫 global）。遍历时用它判断某个块是否属于当前子图。
 *
 * **Kotlin 转换说明**：原 Java 的 `Set<BlockNode>` 直接改为 Kotlin `Set`（JVM 擦除一致），
 * 构造器由 Java 侧以 `new GlobalTraverserSourceState(new HashSet<>(...))` 调用，签名不变。
 */
class GlobalTraverserSourceState(val containedBlocks: Set<BlockNode>) {

	fun isBlockContained(block: BlockNode): Boolean = containedBlocks.contains(block)
}
