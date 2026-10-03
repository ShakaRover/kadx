package jadx.core.dex.visitors.regions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IBranchRegion
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.SwitchRegion
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 删除 void 方法中多余的 `return` 指令。
 *
 * **算法意图**：Java 源码里 void 方法末尾的 `return;` 是编译器自动加上的，
 * 反编译时应去掉。本访问器遍历区域树，当某个块只有一条 `return`、且它后面没有
 * 任何代码（不在循环里、不是 switch 分支）时，就把这条指令删掉。
 *
 * Kotlin 转换说明：继承 [TracedRegionVisitor] 以获得“当前区域栈”，
 * 用于判断块是否处于循环中、以及是否还有后续指令。
 */
class ReturnVisitor : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		if (mth.isVoidReturn()) {
			DepthRegionTraversal.traverse(mth, ReturnRemoverVisitor())
		}
	}

	private class ReturnRemoverVisitor : TracedRegionVisitor() {

		override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
			super.enterRegion(mth, region)
			// switch 的分支不能随便删 return（每个 case 可能各自 return）
			return region !is SwitchRegion
		}

		override fun processBlockTraced(mth: MethodNode, container: IBlock, currentRegion: IRegion) {
			if (container.javaClass != BlockNode::class.java) {
				return
			}
			val block = container as BlockNode
			if (block.contains(AFlag.RETURN)) {
				val insns = block.instructions
				if (insns.size == 1 &&
					blockNotInLoop(mth, block) &&
					noTrailInstructions(block)
				) {
					insns.removeAt(0)
					block.remove(AFlag.RETURN)
				}
			}
		}

		/** 块不在任何循环中时才允许删除 return */
		private fun blockNotInLoop(mth: MethodNode, block: BlockNode): Boolean {
			if (mth.loopsCount == 0) {
				return true
			}
			if (mth.getLoopForBlock(block) != null) {
				return false
			}
			for (region in regionStack) {
				if (region.javaClass == LoopRegion::class.java) {
					return false
				}
			}
			return true
		}

		/**
		 * 检查该块之后（沿区域树向上）没有任何代码。
		 *
		 * 遍历区域栈：从当前块出发逐层向上，只要在当前区域里该容器之后还存在
		 * 非空子块，就说明还有后续指令，不能删 return。
		 */
		private fun noTrailInstructions(block: BlockNode): Boolean {
			var curContainer: IContainer = block
			for (region in regionStack) {
				// 分支区域的其他分支不算“后续代码”，直接跳到该区域本身
				if (region is IBranchRegion) {
					curContainer = region
					continue
				}
				val subBlocks = region.subBlocks
				if (subBlocks.isNotEmpty()) {
					val itSubBlock = subBlocks.listIterator(subBlocks.size)
					while (itSubBlock.hasPrevious()) {
						val subBlock = itSubBlock.previous()
						if (subBlock === curContainer) {
							break
						} else if (!isEmpty(subBlock)) {
							return false
						}
					}
				}
				curContainer = region
			}
			return true
		}

		/**
		 * 判断容器是否为空；一条 `return` 指令不算内容（它稍后会被删除）。
		 */
		private fun isEmpty(container: IContainer): Boolean {
			if (container is IBlock) {
				return container.instructions.isEmpty() || container.contains(AFlag.RETURN)
			} else if (container is IRegion) {
				for (block in container.subBlocks) {
					if (!isEmpty(block)) {
						return false
					}
				}
				return true
			} else {
				throw JadxRuntimeException("Unknown container type: " + container.javaClass)
			}
		}
	}
}
