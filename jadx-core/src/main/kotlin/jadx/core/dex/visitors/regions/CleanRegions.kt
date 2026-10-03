package jadx.core.dex.visitors.regions

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.visitors.AbstractVisitor

/**
 * 清理区域树中的“空壳”。
 *
 * **算法意图**：区域构建与优化过程中会产生一些不再需要生成代码的容器：
 * - 带 [AFlag.DONT_GENERATE] 标记的容器；
 * - 指令被清空的基本块；
 * - 内部子块全部可删除的区域。
 * 这些都应该从父区域中移除，否则会生成空语句。注意：**空的无限循环要保留**
 * （`while (true) {}` 是有语义的）。
 *
 * Kotlin 转换说明：原 Java 静态方法 `process` 放入 companion + `@JvmStatic`，
 * Java 调用方（RegionMakerVisitor、SynchronizedRegionMaker）写法不变。
 */
class CleanRegions : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		process(mth)
	}

	companion object {
		/** 单例访问器，避免每次遍历都新建对象 */
		private val REMOVE_REGION_VISITOR: IRegionVisitor = RemoveRegionVisitor()

		fun process(mth: MethodNode) {
			if (mth.isNoCode() || checkNotNull(mth.basicBlocks).isEmpty()) {
				return
			}
			DepthRegionTraversal.traverse(mth, REMOVE_REGION_VISITOR)
		}
	}

	/** 进入区域时，把该区域中所有“可删除”的子容器移除 */
	private class RemoveRegionVisitor : AbstractRegionVisitor() {
		override fun enterRegion(mth: MethodNode, region: IRegion): Boolean {
			if (region is Region) {
				// Region.subBlocks 实际返回可变的 ArrayList，这里向下转型以便原地删除
				@Suppress("UNCHECKED_CAST")
				(region.subBlocks as MutableList<IContainer>).removeAll { canRemoveRegion(it) }
			}
			return true
		}

		/**
		 * 判断一个容器是否可以被删除。
		 *
		 * 递归规则：区域只有当其所有子块都可删除时才可删除；
		 * 但“无限循环”即使为空也必须保留。
		 */
		private fun canRemoveRegion(container: IContainer): Boolean {
			if (container.contains(AFlag.DONT_GENERATE)) {
				return true
			}
			if (container is BlockNode) {
				return container.instructions.isEmpty()
			}
			if (container is LoopRegion) {
				if (container.isEndless) {
					// 保留空的无限循环
					return false
				}
			}
			if (container is IRegion) {
				val subBlocks = container.subBlocks
				for (subBlock in subBlocks) {
					if (!canRemoveRegion(subBlock)) {
						return false
					}
				}
				return true
			}
			return false
		}
	}
}
