package jadx.core.dex.visitors.regions

import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode

/**
 * 区域树访问器。
 *
 * **用途**：配合 [DepthRegionTraversal] 对区域树做深度优先遍历。
 * 进入区域时回调 [enterRegion]，处理区域内的基本块时回调 [processBlock]，
 * 离开区域时回调 [leaveRegion]。
 *
 * Kotlin 转换说明：纯接口，方法名与参数名保持不变。
 */
interface IRegionVisitor {

	/** 处理一个基本块 */
	fun processBlock(mth: MethodNode, container: IBlock)

	/**
	 * 进入一个区域。
	 *
	 * @return true 表示继续遍历该区域的子块，false 表示跳过其内部
	 */
	fun enterRegion(mth: MethodNode, region: IRegion): Boolean

	/** 离开一个区域（与 [enterRegion] 配对） */
	fun leaveRegion(mth: MethodNode, region: IRegion)
}
