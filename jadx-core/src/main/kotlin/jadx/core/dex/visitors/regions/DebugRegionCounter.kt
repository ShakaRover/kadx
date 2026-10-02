package jadx.core.dex.visitors.regions

import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.AbstractVisitor

/**
 * 调试用：统计区域树的深度与区域数量，并打印每个基本块所处的深度。
 *
 * **用途**：仅用于开发期排查“区域嵌套过深/区域数量异常”的问题，正常反编译流程不启用。
 * 输出的格式为 `深度 : 块 // 指令列表`，最后输出区域总数。
 *
 * Kotlin 转换说明：内部辅助类保持私有；[BlockDepthEntry] 的字段只在包内使用，
 * 声明为普通可变属性即可。
 */
class DebugRegionCounter : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		val visitor = RegionCounterVisitor()
		DepthRegionTraversal.traverse(mth, visitor)
		val sortedBlocks = visitor.getSortedEntries()
		for (x in sortedBlocks) {
			println(x.depth.toString() + " : " + x.block.toString() + " // " + x.block.getInstructions().toString())
		}

		println("nregions :: " + visitor.getNRegions())
	}

	/** 统计区域深度与数量的访问器 */
	private class RegionCounterVisitor : AbstractRegionVisitor() {
		private var depth = 0
		private var nregions = 0
		private val blockDepths: MutableList<BlockDepthEntry> = ArrayList()

		override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
			depth += 1
			nregions += 1
			return true
		}

		override fun processBlock(mth: MethodNode, container: IBlock) {
			if (container is BlockNode) {
				blockDepths.add(BlockDepthEntry(depth, container))
			}
		}

		override fun leaveRegion(mth: MethodNode, region: IRegion) {
			depth -= 1
		}

		/** 按深度升序排序后返回（稳定排序，等价于原 Java 的 comparingInt） */
		fun getSortedEntries(): List<BlockDepthEntry> {
			blockDepths.sortBy { it.depth }
			return blockDepths
		}

		fun getNRegions(): Int = nregions
	}

	/** 基本块及其所在深度 */
	private class BlockDepthEntry(
		var depth: Int,
		var block: BlockNode,
	)
}
