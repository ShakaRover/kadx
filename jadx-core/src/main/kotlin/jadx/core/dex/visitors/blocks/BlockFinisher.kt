package jadx.core.dex.visitors.blocks

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.AbstractVisitor

/**
 * 基本块收尾访问器。
 *
 * **做什么**：等所有基本块相关的 Pass（切分、支配树、循环注册等）都跑完后，
 * 由本访问器把方法的基本块列表与循环列表“冻结”（lock）。
 *
 * **为什么**：冻结后集合会变成不可变视图，后续 Pass 若再试图修改就会立刻抛异常，
 * 这样能尽早发现对已定型 CFG 的非法改动（而不是等到代码生成阶段才出现诡异结果）。
 */
class BlockFinisher : AbstractVisitor() {

	override fun visit(mth: MethodNode) {
		// 无代码、或还没有任何基本块的方法无需处理
		if (mth.isNoCode() || checkNotNull(mth.basicBlocks).isEmpty()) {
			return
		}
		// DISABLE_BLOCKS_LOCK 用于测试/特殊场景下显式跳过冻结
		if (!mth.contains(AFlag.DISABLE_BLOCKS_LOCK)) {
			mth.finishBasicBlocks()
		}
	}
}
