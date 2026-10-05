package kadx.core.dex.visitors.regions

import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.MethodNode

/**
 * “可提前停止”的区域访问器。
 *
 * **用途**：按深度优先顺序访问区域内的所有容器，一旦访问器返回非 null 结果，
 * 遍历立即停止并把该结果作为整体返回值。常用于“查找第一个满足条件的容器”。
 *
 * Kotlin 转换说明：泛型返回类型用可空 `R?` 表达原 Java 的 `@Nullable R`。
 */
interface IRegionPartialVisitor<R> {
	/**
	 * 访问一个容器，直到返回非 null 才停止。
	 *
	 * @return 返回非 null 表示停止访问，并作为 [DepthRegionTraversal.traversePartial] 的结果
	 */
	fun visit(mth: MethodNode, container: IContainer): R?
}
