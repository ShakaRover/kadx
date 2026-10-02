package jadx.core.dex.visitors.regions

import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode

/**
 * 迭代式区域访问器。
 *
 * **用途**：有些区域优化需要反复访问，直到某次访问不再产生变化为止
 * （例如删除多余 else 块后可能又出现新的可优化结构）。
 * 访问器通过返回值告诉遍历器是否需要“从头再来”。
 *
 * Kotlin 转换说明：原 Java 接口只有单个抽象方法，Java 侧可以用 lambda 实现；
 * 转成 Kotlin 后必须声明为 `fun interface` 才能继续支持 SAM 转换（Kotlin 与 Java 均可）。
 */
fun interface IRegionIterativeVisitor {

	/**
	 * 访问一个区域。
	 *
	 * @return 返回 true 时，遍历会重新开始（restart）
	 */
	fun visitRegion(mth: MethodNode, region: IRegion): Boolean
}
