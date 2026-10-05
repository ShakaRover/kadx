package kadx.core.dex.visitors.blocks

import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.SpecialEdgeAttr
import kadx.core.dex.attributes.nodes.SpecialEdgeAttr.SpecialEdgeType
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.ListUtils

/**
 * 修复“多入口循环”（multi-entry loop）。
 *
 * **背景**：正常自然循环只有一个入口（循环头）。但某些编译器生成的代码会让循环体
 * 从多个位置跳入，导致后续区域分析无法识别出合法循环。本类先通过 DFS 着色找出
 * 回边（BACK_EDGE）与交叉边（CROSS_EDGE），再针对两种可修复模式复制块，
 * 把多入口循环改造成单入口循环。
 *
 * **两种模式**：
 * 1. [isHeaderSuccessorEntry]：从“循环头直接后继”进入循环；
 * 2. [isEndBlockEntry]：从“循环尾块”进入循环。
 *
 * **Kotlin 转换说明**：原类只有静态方法，转为 `object` + `@JvmStatic`。
 * 涉及块身份判断处一律用 `===`（引用比较）。
 */
object FixMultiEntryLoops {

	fun process(mth: MethodNode): Boolean {
		try {
			detectSpecialEdges(mth)
		} catch (e: Exception) {
			mth.addWarnComment("Failed to detect multi-entry loops", e)
			return false
		}
		val specialEdges = mth.getAll(AType.SPECIAL_EDGE)
		val multiEntryLoops = ArrayList<SpecialEdgeAttr>()
		for (e in specialEdges) {
			if (e.type == SpecialEdgeType.BACK_EDGE && !isSingleEntryLoop(e)) {
				multiEntryLoops.add(e)
			}
		}
		if (multiEntryLoops.isEmpty()) {
			return false
		}
		try {
			val crossEdges = ListUtils.filter(specialEdges) { e -> e.type == SpecialEdgeType.CROSS_EDGE }
			var changed = false
			for (backEdge in multiEntryLoops) {
				// 注意：这里必须先执行 fixLoop，不能短路
				changed = fixLoop(mth, backEdge, crossEdges) || changed
			}
			return changed
		} catch (e: Exception) {
			mth.addWarnComment("Failed to fix multi-entry loops", e)
			return false
		}
	}

	private fun fixLoop(mth: MethodNode, backEdge: SpecialEdgeAttr, crossEdges: List<SpecialEdgeAttr>): Boolean {
		if (isHeaderSuccessorEntry(mth, backEdge, crossEdges)) {
			return true
		}
		if (isEndBlockEntry(mth, backEdge, crossEdges)) {
			return true
		}
		mth.addWarnComment("Unsupported multi-entry loop pattern ($backEdge). Please report as a decompilation issue!!!")
		return false
	}

	/** 模式 1：交叉边从“循环头的直接支配者”跳到循环头后继，复制循环头修复。 */
	private fun isHeaderSuccessorEntry(mth: MethodNode, backEdge: SpecialEdgeAttr, crossEdges: List<SpecialEdgeAttr>): Boolean {
		val header = backEdge.end
		val headerIDom = header.idom
		val subEntry = ListUtils.filterOnlyOne(crossEdges) { e -> e.start === headerIDom }
		if (subEntry == null || !ListUtils.isSingleElement(header.successors, subEntry.end)) {
			return false
		}
		val loopEnd = backEdge.start
		val subEntryBlock = subEntry.end
		val copyHeader = BlockSplitter.insertBlockBetween(mth, loopEnd, header)
		BlockSplitter.copyBlockData(header, copyHeader)
		BlockSplitter.replaceConnection(copyHeader, header, subEntryBlock)
		mth.addDebugComment("Duplicate block ($header) to fix multi-entry loop: $backEdge")
		return true
	}

	/** 模式 2：交叉边从别处跳到循环尾块，复制“中间块”修复。 */
	private fun isEndBlockEntry(mth: MethodNode, backEdge: SpecialEdgeAttr, crossEdges: List<SpecialEdgeAttr>): Boolean {
		val loopEnd = backEdge.start
		val subEntry = ListUtils.filterOnlyOne(crossEdges) { e -> e.end === loopEnd }
		if (subEntry == null) {
			return false
		}
		dupPath(mth, subEntry.start, loopEnd, backEdge.end)
		mth.addDebugComment("Duplicate block ($loopEnd) to fix multi-entry loop: $backEdge")
		return true
	}

	/**
	 * 在从 [start] 到 [end] 的路径上复制 [center] 块。
	 */
	private fun dupPath(mth: MethodNode, start: BlockNode, center: BlockNode, end: BlockNode) {
		val copyCenter = BlockSplitter.insertBlockBetween(mth, start, end)
		BlockSplitter.copyBlockData(center, copyCenter)
		BlockSplitter.removeConnection(start, center)
	}

	private fun isSingleEntryLoop(e: SpecialEdgeAttr): Boolean {
		val header = e.end
		val loopEnd = e.start
		// 头部等于尾部（自环），或头部支配尾部，都视为单入口
		return header === loopEnd || checkNotNull(loopEnd.doms).get(header.pos)
	}

	private enum class BlockColor {
		WHITE,
		GRAY,
		BLACK,
	}

	private fun detectSpecialEdges(mth: MethodNode) {
		val colors = Array(checkNotNull(mth.basicBlocks).size) { BlockColor.WHITE }
		colorDFS(mth, colors, checkNotNull(mth.enterBlock))
	}

	/**
	 * 三色 DFS：白色=未访问，灰色=正在访问栈上，黑色=已完成。
	 * 指向灰色节点的边是回边（构成循环），指向黑色节点的边是交叉边。
	 */
	private fun colorDFS(mth: MethodNode, colors: Array<BlockColor>, block: BlockNode) {
		colors[block.pos] = BlockColor.GRAY
		for (v in block.successors) {
			when (colors[v.pos]) {
				BlockColor.WHITE -> colorDFS(mth, colors, v)
				BlockColor.GRAY -> mth.addAttr(AType.SPECIAL_EDGE, SpecialEdgeAttr(SpecialEdgeType.BACK_EDGE, block, v))
				BlockColor.BLACK -> mth.addAttr(AType.SPECIAL_EDGE, SpecialEdgeAttr(SpecialEdgeType.CROSS_EDGE, block, v))
			}
		}
		colors[block.pos] = BlockColor.BLACK
	}
}
