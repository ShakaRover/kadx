package jadx.core.dex.visitors.regions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBranchRegion
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.AbstractRegion
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.TryCatchRegion
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.trycatch.TryCatchBlockAttr
import jadx.core.utils.RegionUtils

/**
 * 把 try 代码体提取成独立的 try/catch 区域。
 *
 * **算法意图**：异常处理在 CFG 层表现为“异常处理器块 + try 块集合”。
 * 区域构建阶段先按普通控制流还原，本访问器再找出每个 try 块的“顶层分割块”
 * （[TryCatchBlockAttr.getTopSplitter]），把被它支配、且不属于异常处理器路径的块
 * 包进 [TryCatchRegion]。
 *
 * Kotlin 转换说明：
 * - 原 Java 用 lambda 实现 [IRegionIterativeVisitor]，因为该接口已声明为 `fun interface`，
 *   Kotlin 侧同样可以直接用 lambda；
 * - 原 Java 的 `a == b` 是引用比较，这里必须写成 `===`。
 */
class ProcessTryCatchRegions : AbstractRegionVisitor() {

	companion object {
		fun process(mth: MethodNode) {
			if (mth.isNoCode() || mth.isNoExceptionHandlers()) {
				return
			}
			val tryBlocks = collectTryCatchBlocks(mth)
			if (tryBlocks.isEmpty()) {
				return
			}
			DepthRegionTraversal.traverseIncludingExcHandlers(mth) { regionMth, region ->
				val changed = checkAndWrap(regionMth, tryBlocks, region)
				changed && tryBlocks.isNotEmpty()
			}
		}

		/** 收集方法的全部 try 块，并把父 try 块排到前面（便于先处理外层） */
		private fun collectTryCatchBlocks(mth: MethodNode): MutableList<TryCatchBlockAttr> {
			val list = mth.getAll(AType.TRY_BLOCKS_LIST)
			if (list.isEmpty()) {
				return ArrayList()
			}
			val tryBlocks = ArrayList(list)
			// 父 try 块排到顶部
			tryBlocks.sortWith(
				Comparator { a, b ->
					when {
						a === b -> 0
						a.getOuterTryBlock() === b -> 1
						else -> -1
					}
				},
			)
			return tryBlocks
		}

		/** 在当前区域里查找 try 块的顶层分割块；找到就尝试包裹并返回 true */
		private fun checkAndWrap(mth: MethodNode, tryBlocks: MutableList<TryCatchBlockAttr>, region: IRegion): Boolean {
			// 只需在本层查找顶层分割块，无需深入子区域
			for (tb in tryBlocks) {
				val topSplitter = tb.getTopSplitter()
				if (topSplitter != null && region.subBlocks.contains(topSplitter)) {
					if (!wrapBlocks(region, tb, topSplitter)) {
						mth.addWarn("Can't wrap try/catch for region: " + region)
					}
					tryBlocks.remove(tb)
					return true
				}
			}
			return false
		}

		/**
		 * 把所有被 [dominator] 支配的块提取到独立区域，并标记为 try/catch 块。
		 */
		private fun wrapBlocks(replaceRegion: IRegion?, tb: TryCatchBlockAttr, dominator: BlockNode): Boolean {
			if (replaceRegion == null) {
				return false
			}
			if (replaceRegion is LoopRegion) {
				return wrapBlocks(replaceRegion.body, tb, dominator)
			}
			if (replaceRegion is IBranchRegion) {
				return wrapBlocks(replaceRegion.parent, tb, dominator)
			}

			val tryRegion = Region(replaceRegion)
			val subBlocks = replaceRegion.subBlocks
			// 遍历外层区域：凡是从 dominator 可达、且不是异常处理器路径的块，
			// 都属于 try 体，应放进 tryRegion。
			for (cont in subBlocks) {
				if (RegionUtils.hasPathThroughBlock(dominator, cont)) {
					if (isHandlerPath(tb, cont)) {
						// 该块从异常处理器可达，说明位于 try 体之后，跳过
						continue
					}
					tryRegion.add(cont)
				}
			}
			if (tryRegion.subBlocks.isEmpty()) {
				return false
			}

			val tryCatchRegion = TryCatchRegion(replaceRegion, tryRegion)
			tryRegion.parent = tryCatchRegion
			tryCatchRegion.setTryCatchBlock(tb)

			// 用 try/catch 区域替换原来的第一个子块
			val firstNode = tryRegion.subBlocks[0]
			if (!replaceRegion.replaceSubBlock(firstNode, tryCatchRegion)) {
				return false
			}
			@Suppress("UNCHECKED_CAST")
			(subBlocks as MutableList<IContainer>).removeAll(tryRegion.subBlocks)

			// 修正 tryRegion 子块的父指针
			for (cont in tryRegion.subBlocks) {
				if (cont is AbstractRegion) {
					cont.parent = tryRegion
				}
			}
			return true
		}

		/** 判断容器是否位于某个异常处理器的可达路径上（即 try 体之后） */
		private fun isHandlerPath(tb: TryCatchBlockAttr, container: IContainer): Boolean {
			for (h in tb.handlers) {
				val handlerBlock = h.getHandlerBlock()
				if (handlerBlock != null &&
					!handlerBlock.contains(AFlag.REMOVE) &&
					RegionUtils.isPathExists(handlerBlock, container)
				) {
					return true
				}
			}
			return false
		}
	}
}
