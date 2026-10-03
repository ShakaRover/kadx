package jadx.core.dex.visitors.blocks

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.BlockUtils
import jadx.core.utils.EmptyBitSet
import java.util.ArrayList
import java.util.BitSet

/**
 * 后支配树（Post-Dominator Tree）构建。
 *
 * **与支配树的关系**：后支配树就是把“前驱/后继”对调、从方法出口块出发做支配分析，
 * 因此可以直接复用 [DominatorTree.build]（传入后继函数）与 [DominatorTree.collectDoms]。
 *
 * **为什么要重映射位置**：算法要求节点编号与逆后序一致，所以这里先按“逆向 DFS”的顺序
 * 临时改写各块的 `pos`，算完后再把位图里的下标映射回原始位置，并在 finally 中还原 `pos`。
 *
 * **只对带 [AFlag.COMPUTE_POST_DOM] 标记的方法计算**（该信息并非所有方法都需要）。
 *
 * **Kotlin 转换说明**：原类只有静态方法，转为 `object` + `@JvmStatic`。
 */
object PostDominatorTree {

	fun compute(mth: MethodNode) {
		if (!mth.contains(AFlag.COMPUTE_POST_DOM)) {
			return
		}
		try {
			val mthBlocksCount = checkNotNull(mth.basicBlocks).size
			val sorted = ArrayList<BlockNode>(mthBlocksCount)
			BlockUtils.visitReverseDFS(mth) { b -> sorted.add(b) }
			// 临时把块位置改成“逆后序”下标，并保存旧位置用于稍后重映射
			val blocksCount = sorted.size
			val posMapping = IntArray(mthBlocksCount)
			for (i in 0 until blocksCount) {
				posMapping[i] = sorted[i].pos
			}
			BlockNode.updateBlockPositions(sorted)

			val postDoms = DominatorTree.build(sorted) { b -> b.successors }
			val firstBlock = sorted[0]
			firstBlock.postDoms = EmptyBitSet.EMPTY
			firstBlock.iPostDom = null
			for (i in 1 until blocksCount) {
				val block = sorted[i]
				val iPostDom = checkNotNull(postDoms[i])
				block.iPostDom = iPostDom
				val postDomBS = DominatorTree.collectDoms(postDoms, iPostDom)
				block.postDoms = postDomBS
			}
			// 把位图下标从“逆后序”映射回原始位置
			for (i in 1 until blocksCount) {
				val block = sorted[i]
				val bs = BitSet(blocksCount)
				val postDomBS = checkNotNull(block.postDoms)
				var n = postDomBS.nextSetBit(0)
				while (n >= 0) {
					bs.set(posMapping[n])
					n = postDomBS.nextSetBit(n + 1)
				}
				bs.clear(posMapping[i])
				block.postDoms = bs
			}
			// 检查 sorted 中缺失的块（通常由死循环导致）
			val blocksDelta = mthBlocksCount - blocksCount
			if (blocksDelta != 0) {
				var insnsCount = 0
				for (block in checkNotNull(mth.basicBlocks)) {
					if (block.postDoms == null) {
						block.postDoms = EmptyBitSet.EMPTY
						block.iPostDom = null
						insnsCount += block.instructions.size
					}
				}
				mth.addInfoComment("Infinite loop detected, blocks: $blocksDelta, insns: $insnsCount")
			}
		} catch (e: StackOverflowError) {
			// 该信息并非总是必需，失败只记警告而不中断
			mth.addWarnComment("Failed to build post-dominance tree", e)
		} catch (e: Exception) {
			mth.addWarnComment("Failed to build post-dominance tree", e)
		} finally {
			// 还原块的原始位置编号
			mth.updateBlockPositions()
		}
	}
}
