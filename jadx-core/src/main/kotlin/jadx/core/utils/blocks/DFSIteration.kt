package jadx.core.utils.blocks

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.MethodNode
import java.util.ArrayDeque
import java.util.Deque
import java.util.function.Function

/**
 * 深度优先遍历（DFS）迭代器。
 *
 * **用途**：以显式栈（[ArrayDeque]）实现非递归 DFS，便于在遍历过程中随时暂停/恢复，
 * 例如沿支配树路径回溯时逐步取块。
 *
 * **遍历顺序**：使用 `addLast` + `pollLast` 组成后进先出栈；压栈时倒序压入后继，
 * 从而保证出栈顺序与后继列表顺序一致。
 *
 * **Kotlin 转换说明**：构造器保留原 `(MethodNode, BlockNode, Function)` 形态，
 * 以便上游 Kotlin 继续用尾随 lambda 传入“取后继”函数。
 */
class DFSIteration(
	mth: MethodNode,
	startBlock: BlockNode,
	next: Function<BlockNode, List<BlockNode>>,
) {
	private val nextFunc: Function<BlockNode, List<BlockNode>> = next
	private val queue: Deque<BlockNode> = ArrayDeque()
	private val visited: BlockSet = BlockSet(mth)

	init {
		queue.addLast(startBlock)
		visited.add(startBlock)
	}

	/** 返回下一个待访问块；栈空时返回 null。 */
	fun next(): BlockNode? {
		val current = queue.pollLast() ?: return null
		val nextBlocks = nextFunc.apply(current)
		val count = nextBlocks.size
		// 倒序压栈，保证弹出顺序与后继列表顺序一致
		for (i in count - 1 downTo 0) {
			val next = nextBlocks[i]
			if (!visited.addChecked(next)) {
				queue.addLast(next)
			}
		}
		return current
	}
}
