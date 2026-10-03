package jadx.core.codegen

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.TargetInsnNode
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.visitors.blocks.BlockProcessor
import jadx.core.dex.visitors.blocks.BlockSplitter
import jadx.core.utils.BlockUtils
import java.util.ArrayList
import java.util.BitSet

/**
 * “简单模式”反编译辅助器：直接按基本块顺序生成代码，不做区域（region）结构化重构。
 *
 * **用途**：当区域分析失败或用户显式选择 SIMPLE 模式时，按块顺序输出并用 `goto`/标签连接，
 * 这样即使无法恢复 `if`/`while` 结构，也能得到可编译的代码。
 *
 * 本类负责在生成前整理块（删除空块、解绑异常处理器），并计算哪些块需要起始标签 / 结束 goto。
 */
class SimpleModeHelper(private val mth: MethodNode) {

	/** 需要输出起始标签的块集合（按块 id 索引）。 */
	private val startLabel: BitSet = BlockUtils.newBlocksBitSet(mth)

	/** 需要在块末尾补 `goto` 的块集合（按块 id 索引）。 */
	private val endGoto: BitSet = BlockUtils.newBlocksBitSet(mth)

	/** 预处理基本块并返回按 DFS 排序、去除入口/出口块后的列表。 */
	fun prepareBlocks(): List<BlockNode> {
		removeEmptyBlocks()
		val blocksList = sortedBlocks
		blocksList.removeIf { b -> b == mth.enterBlock || b == mth.exitBlock }
		unbindExceptionHandlers()
		if (blocksList.isEmpty()) {
			return ArrayList()
		}
		var prev: BlockNode? = null
		val blocksCount = blocksList.size
		for (i in 0 until blocksCount) {
			val block = blocksList[i]
			val nextBlock = if (i + 1 == blocksCount) null else blocksList[i + 1]
			val preds = block.predecessors
			val predsCount = preds.size
			if (predsCount > 1) {
				startLabel.set(block.id)
			} else if (predsCount == 1 && prev != null) {
				if (prev != preds[0]) {
					if (!block.contains(AFlag.EXC_BOTTOM_SPLITTER)) {
						startLabel.set(block.id)
					}
					if (prev.successors.size == 1 && !mth.isPreExitBlock(prev)) {
						endGoto.set(prev.id)
					}
				}
			}
			val lastInsn = BlockUtils.getLastInsn(block)
			if (lastInsn is TargetInsnNode) {
				processTargetInsn(block, lastInsn, nextBlock)
			}
			if (block.contains(AType.EXC_HANDLER)) {
				startLabel.set(block.id)
			}
			if (nextBlock == null && !mth.isPreExitBlock(block)) {
				endGoto.set(block.id)
			}
			prev = block
		}
		if (mth.isVoidReturn()) {
			val last = blocksList.size - 1
			if (blocksList[last].contains(AFlag.RETURN)) {
				// remove trailing return
				blocksList.removeAt(last)
			}
		}
		return blocksList
	}

	/** 删除“空指令、有前驱、只有一个后继”的块，并把前驱直接连到后继。 */
	private fun removeEmptyBlocks() {
		for (block in checkNotNull(mth.basicBlocks)) {
			if (block.instructions.isEmpty() &&
				block.predecessors.size > 0 &&
				block.successors.size == 1
			) {
				val successor = block.successors[0]
				val predecessors = block.predecessors
				BlockSplitter.removeConnection(block, successor)
				if (predecessors.size == 1) {
					BlockSplitter.replaceConnection(predecessors[0], block, successor)
				} else {
					for (pred in ArrayList(predecessors)) {
						BlockSplitter.replaceConnection(pred, block, successor)
					}
				}
				block.add(AFlag.REMOVE)
			}
		}
		BlockProcessor.removeMarkedBlocks(mth)
	}

	/** 解绑异常处理器与处理块的连接，简单模式下异常边由块顺序体现。 */
	private fun unbindExceptionHandlers() {
		if (mth.isNoExceptionHandlers()) {
			return
		}
		for (handler in mth.getExceptionHandlers()) {
			val handlerBlock = handler.getHandlerBlock()
			if (handlerBlock != null) {
				BlockSplitter.removePredecessors(handlerBlock)
			}
		}
	}

	/** 处理块末尾的分支指令：决定后继块的标签与 `if` 取反。 */
	private fun processTargetInsn(block: BlockNode, lastInsn: InsnNode, next: BlockNode?) {
		if (lastInsn is IfNode) {
			val thenBlock = lastInsn.getThenBlock()
			if (next == thenBlock) {
				lastInsn.invertCondition()
				startLabel.set(checkNotNull(lastInsn.getThenBlock()).id)
			} else {
				startLabel.set(checkNotNull(thenBlock).id)
			}
			lastInsn.normalize()
		} else {
			for (successor in block.successors) {
				startLabel.set(successor.id)
			}
		}
	}

	fun isNeedStartLabel(block: BlockNode): Boolean = startLabel.get(block.id)

	fun isNeedEndGoto(block: BlockNode): Boolean = endGoto.get(block.id)

	/** 用 DFS 顺序遍历基本块，减少生成的 `goto` 数量。 */
	private val sortedBlocks: MutableList<BlockNode>
		get() {
			val list = ArrayList<BlockNode>(checkNotNull(mth.basicBlocks).size)
			BlockUtils.visitDFS(mth) { list.add(it) }
			return list
		}
}
