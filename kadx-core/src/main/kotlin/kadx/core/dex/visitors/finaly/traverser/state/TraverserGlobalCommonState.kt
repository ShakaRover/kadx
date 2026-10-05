package kadx.core.dex.visitors.finaly.traverser.state

import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.Pair

/**
 * 一次 finally 匹配过程中的“公共缓存状态”。
 *
 * **用途**：遍历器会反复比较同一对 (finally 块, 候选块)。为避免重复搜索，
 * 这里缓存“已经比较过的块对 -> 该块对产生的所有路径状态”，后续命中缓存即可直接复用。
 *
 * **Kotlin 转换说明**：`searchedStates` 键是 `Pair<BlockNode>`（两个同类型对象的二元组），
 * 保持原有 HashMap 语义；`getCachedStateFor` 原 Java 允许返回 null。
 */
class TraverserGlobalCommonState(private val mth: MethodNode) {

	private val searchedStates: MutableMap<Pair<BlockNode>, List<TraverserActivePathState>> = HashMap()

	fun addCachedStateFor(finallyBlock: BlockNode, candidateBlock: BlockNode, state: List<TraverserActivePathState>) {
		val blocks = Pair(finallyBlock, candidateBlock)
		searchedStates[blocks] = state
	}

	fun getCachedStateFor(finallyBlock: BlockNode, candidateBlock: BlockNode): List<TraverserActivePathState>? {
		val blocks = Pair(finallyBlock, candidateBlock)
		return searchedStates[blocks]
	}

	fun hasBlocksBeenCached(finallyBlock: BlockNode, candidateBlock: BlockNode): Boolean {
		val blocks = Pair(finallyBlock, candidateBlock)
		return searchedStates.containsKey(blocks)
	}

	val methodNode: MethodNode get() = mth
}
