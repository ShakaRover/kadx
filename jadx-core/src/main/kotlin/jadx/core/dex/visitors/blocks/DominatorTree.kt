package jadx.core.dex.visitors.blocks

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.BlockUtils
import jadx.core.utils.EmptyBitSet
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.BitSet

/**
 * 支配树（Dominator Tree）构建。
 *
 * **算法来源**：Cooper, Keith D.; Harvey, Timothy J; Kennedy, Ken (2001).
 * "A Simple, Fast Dominance Algorithm".
 * http://www.hipersoft.rice.edu/grads/publications/dom14.pdf
 *
 * **核心概念**：
 * - 若从入口到节点 n 的每条路径都必须经过节点 d，则称 d 支配（dominate）n；
 * - 直接支配节点（idom）是 n 的支配者中“离 n 最近”的那个；
 * - 支配边界（dominance frontier）是“n 支配其前驱、但不严格支配该节点自身”的节点集合，
 *   SSA 构造 PHI 指令时正需要它。
 *
 * **实现要点**：算法给每个块一个逆后序（DFS 后序的逆）编号，然后迭代求交
 * （[intersect]）直到不再变化；用位图 [BitSet] 缓存已算好的支配集合，避免重复上溯。
 *
 * **Kotlin 转换说明**：原类只有静态方法，转为 `object`，公开方法保留 `@JvmStatic`
 * 以维持 Java 侧的静态调用方式。
 */
object DominatorTree {

	@JvmStatic
	fun compute(mth: MethodNode) {
		val sorted = sortBlocks(mth)
		// 普通支配树：沿前驱方向迭代（入口块是树的根）
		val doms = build(sorted) { b -> b.getPredecessors() }
		apply(sorted, doms)
	}

	/**
	 * 按 DFS 逆后序重排方法的基本块，并把重排结果写回方法。
	 *
	 * 逆后序能保证：除入口外，每个块至少有一个前驱排在自己前面，从而让迭代算法收敛。
	 */
	private fun sortBlocks(mth: MethodNode): List<BlockNode> {
		val blocksCount = checkNotNull(mth.getBasicBlocks()).size
		val sorted = ArrayList<BlockNode>(blocksCount)
		BlockUtils.visitDFS(mth) { b -> sorted.add(b) }
		if (sorted.size != blocksCount) {
			throw JadxRuntimeException("Found unreachable blocks")
		}
		mth.setBasicBlocks(sorted)
		return sorted
	}

	/**
	 * 迭代计算每个块的直接支配节点（idom）。
	 *
	 * @param predFunc 取“前驱”或“后继”的函数：普通支配树用前驱，后支配树用后继
	 * @return 下标与 [sorted] 对应的 idom 数组
	 */
	internal fun build(sorted: List<BlockNode>, predFunc: (BlockNode) -> List<BlockNode>): Array<BlockNode?> {
		val blocksCount = sorted.size
		val doms = arrayOfNulls<BlockNode>(blocksCount)
		doms[0] = sorted[0]
		var changed = true
		while (changed) {
			changed = false
			for (blockId in 1 until blocksCount) {
				val b = sorted[blockId]
				val preds = predFunc(b)
				var pickedPred = -1
				var newIDom: BlockNode? = null
				// 先取第一个“已算出 idom”的前驱作为初始候选
				for (pred in preds) {
					val id = pred.pos
					if (doms[id] != null) {
						newIDom = pred
						pickedPred = id
						break
					}
				}
				if (newIDom == null) {
					throw JadxRuntimeException("No immediate dominator for block: $b")
				}
				// 与其余已算出的前驱逐个求交
				for (predBlock in preds) {
					val predId = predBlock.pos
					if (predId == pickedPred) {
						continue
					}
					if (doms[predId] != null) {
						newIDom = intersect(sorted, doms, predBlock, checkNotNull(newIDom))
					}
				}
				if (doms[blockId] !== newIDom) {
					doms[blockId] = newIDom
					changed = true
				}
			}
		}
		return doms
	}

	/**
	 * 求两个块在支配树上的最近公共祖先（沿 idom 链向上“相遇”）。
	 */
	private fun intersect(sorted: List<BlockNode>, doms: Array<BlockNode?>, b1: BlockNode, b2: BlockNode): BlockNode {
		var f1 = b1.pos
		var f2 = b2.pos
		while (f1 != f2) {
			while (f1 > f2) {
				f1 = checkNotNull(doms[f1]).pos
			}
			while (f2 > f1) {
				f2 = checkNotNull(doms[f2]).pos
			}
		}
		return sorted[f1]
	}

	/**
	 * 把算好的 idom 写回块，并填充每个块的支配集合 [BlockNode.doms]。
	 */
	private fun apply(sorted: List<BlockNode>, doms: Array<BlockNode?>) {
		val enterBlock = sorted[0]
		enterBlock.doms = EmptyBitSet.EMPTY
		enterBlock.idom = null
		val blocksCount = sorted.size
		for (i in 1 until blocksCount) {
			val block = sorted[i]
			val idom = checkNotNull(doms[i])
			block.idom = idom
			idom.addDominatesOn(block)
			val domBS = collectDoms(doms, idom)
			domBS.clear(i)
			block.doms = domBS
		}
	}

	/**
	 * 收集某个块的支配集合（含它自身）。
	 *
	 * 若祖先块已经缓存了支配集合，就直接按位或复用，避免重复上溯。
	 */
	internal fun collectDoms(doms: Array<BlockNode?>, idom: BlockNode): BitSet {
		val domBS = BitSet(doms.size)
		var nextIDom = idom
		while (true) {
			val id = nextIDom.pos
			if (domBS.get(id)) {
				break
			}
			domBS.set(id)
			val curDoms = nextIDom.doms
			if (curDoms != null) {
				// 祖先已有缓存：直接合并即可
				domBS.or(curDoms)
				break
			}
			nextIDom = checkNotNull(doms[id])
		}
		return domBS
	}

	/**
	 * 计算支配边界（dominance frontier）。
	 *
	 * 做法：对每个有 >=2 个前驱的块，沿着各前驱的 idom 链向上走，
	 * 直到走到该块的 idom 为止，沿途经过的块都把当前块加入自己的支配边界。
	 */
	@JvmStatic
	fun computeDominanceFrontier(mth: MethodNode) {
		val blocks = checkNotNull(mth.getBasicBlocks())
		for (block in blocks) {
			block.domFrontier = null
		}
		val blocksCount = blocks.size
		for (block in blocks) {
			val preds = block.getPredecessors()
			if (preds.size >= 2) {
				val idom = block.idom
				for (pred in preds) {
					var runner: BlockNode? = pred
					while (runner !== idom) {
						val current = checkNotNull(runner)
						addToDF(current, block, blocksCount)
						runner = current.idom
					}
				}
			}
		}
		// 没有支配边界的块统一替换成共享的空位图单例，节省内存
		for (block in blocks) {
			val df = block.domFrontier
			if (df == null || df.isEmpty()) {
				block.domFrontier = EmptyBitSet.EMPTY
			}
		}
	}

	private fun addToDF(block: BlockNode, dfBlock: BlockNode, blocksCount: Int) {
		var df = block.domFrontier
		if (df == null) {
			df = BitSet(blocksCount)
			block.domFrontier = df
		}
		df.set(dfBlock.pos)
	}
}
