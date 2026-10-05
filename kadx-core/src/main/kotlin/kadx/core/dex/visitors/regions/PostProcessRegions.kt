package kadx.core.dex.visitors.regions

import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnContainer
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.Region
import kadx.core.dex.regions.SwitchRegion
import kadx.core.dex.regions.loops.LoopRegion
import kadx.core.dex.visitors.regions.maker.SwitchRegionMaker

/**
 * 区域构建后的“收尾处理”。
 *
 * **算法意图**：在区域树初步成形后，按离开区域的顺序做三件事：
 * 1. 循环区域：把前置块的条件合并进循环条件（[LoopRegion.mergePreCondition]）；
 * 2. switch 区域：为各 case 补上 `break`（[SwitchRegionMaker.insertBreaks]）；
 * 3. 普通区域：把挂在“边”上的指令（如 break/continue）插入到最后一个子块。
 *
 * Kotlin 转换说明：本类只有一个单例实例和若干静态方法，私有构造器禁止外部 new。
 */
class PostProcessRegions private constructor() : AbstractRegionVisitor() {

	override fun leaveRegion(mth: MethodNode, region: IRegion) {
		if (region is LoopRegion) {
			// 合并循环条件
			region.mergePreCondition()
		} else if (region is SwitchRegion) {
			SwitchRegionMaker.insertBreaks(mth, region)
		} else if (region is Region) {
			insertEdgeInsn(region)
		}
	}

	companion object {
		private val INSTANCE: IRegionVisitor = PostProcessRegions()

		fun process(mth: MethodNode) {
			DepthRegionTraversal.traverse(mth, INSTANCE)
		}

		/**
		 * 把“边指令属性”（[AType.EDGE_INSN]）中的指令插入区域末尾。
		 *
		 * 仅当属性记录的起点就是区域的最后一个子块时才处理：
		 * 若最后一个子块是空的基本块，直接把指令塞进该块；否则新建一个 [InsnContainer]。
		 */
		private fun insertEdgeInsn(region: Region) {
			val subBlocks = region.subBlocks
			if (subBlocks.isEmpty()) {
				return
			}
			val last = subBlocks[subBlocks.size - 1]
			val edgeInsnAttrs = last.getAll(AType.EDGE_INSN)
			if (edgeInsnAttrs.isEmpty()) {
				return
			}
			val insnAttr = edgeInsnAttrs[0]
			if (insnAttr.start != last) {
				return
			}
			if (last is BlockNode) {
				if (last.instructions.isEmpty()) {
					last.instructions.add(insnAttr.insn)
					return
				}
			}
			region.add(InsnContainer(insnAttr.insn))
		}
	}
}
