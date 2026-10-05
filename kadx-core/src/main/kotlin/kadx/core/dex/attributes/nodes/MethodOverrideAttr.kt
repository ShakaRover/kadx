package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.PinnedAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.IMethodDetails
import kadx.core.dex.nodes.MethodNode
import java.util.SortedSet

/**
 * 方法覆写属性：记录方法覆写层级上的全部相关信息。
 *
 * **三个字段的语义**：
 * - [overrideList]：当前方法所覆写的所有方法（不含自身；基类方法为空列表）；
 * - [relatedMthNodes]：覆写层级上的所有 [MethodNode]（含自身，按可排序集合存放）；
 * - [baseMethods]：被覆写方法对应的 [IMethodDetails] 集合。
 *
 * **Kotlin 转换说明**：[relatedMthNodes] 原 Java 有 setter，故声明为 `var` 属性，
 * 生成的 `getRelatedMthNodes()` / `setRelatedMthNodes()` 与原 JVM 方法名一致。
 */
class MethodOverrideAttr(
	val overrideList: List<IMethodDetails>,
	var relatedMthNodes: SortedSet<MethodNode>,
	val baseMethods: Set<IMethodDetails>,
) : PinnedAttribute() {

	override val attrType: AType<MethodOverrideAttr> get() = AType.METHOD_OVERRIDE

	override fun toString(): String = "METHOD_OVERRIDE: " + baseMethods
}
