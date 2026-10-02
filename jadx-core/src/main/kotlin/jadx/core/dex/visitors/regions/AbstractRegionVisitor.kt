package jadx.core.dex.visitors.regions

import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode

/**
 * [IRegionVisitor] 的默认空实现。
 *
 * **用途**：大多数访问器只关心三个回调中的一个（例如只关心 [enterRegion]），
 * 继承本类后只覆写需要的方法即可，其余保持空操作。
 *
 * Kotlin 转换说明：类与三个方法都保持 `open`，以便 Java/Kotlin 子类继续覆写。
 */
abstract class AbstractRegionVisitor : IRegionVisitor {

	/** 默认进入所有区域（返回 true） */
	override fun enterRegion(mth: MethodNode, region: IRegion): Boolean = true

	/** 默认不处理基本块 */
	override fun processBlock(mth: MethodNode, block: IBlock) {
	}

	/** 默认离开区域时不做任何事 */
	override fun leaveRegion(mth: MethodNode, region: IRegion) {
	}
}
