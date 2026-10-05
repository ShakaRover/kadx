package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.AttrList
import kadx.core.dex.nodes.BlockNode

/**
 * 特殊边属性：描述 CFG 中需要额外关注的边。
 *
 * **两种边**（[SpecialEdgeType]）：
 * - [SpecialEdgeType.BACK_EDGE]：回边（循环尾指向循环头）；
 * - [SpecialEdgeType.CROSS_EDGE]：交叉边（多入口循环里跨区域的跳转）。
 *
 * 该属性是“列表型”属性，一个方法上可挂多条，故 [getAttrType] 返回
 * `AType<AttrList<SpecialEdgeAttr>>`。
 *
 * **Kotlin 转换说明**：嵌套枚举在 Kotlin 中用 `enum class` 表示（非 inner），
 * Java 调用方仍写 `SpecialEdgeAttr.SpecialEdgeType.BACK_EDGE`。
 */
class SpecialEdgeAttr(
	val type: SpecialEdgeType,
	val start: BlockNode,
	val end: BlockNode,
) : IKadxAttribute {

	/** 特殊边的类型 */
	enum class SpecialEdgeType {
		/** 回边：循环尾 -> 循环头 */
		BACK_EDGE,

		/** 交叉边：跨区域跳转 */
		CROSS_EDGE,
	}

	override val attrType: AType<AttrList<SpecialEdgeAttr>> get() = AType.SPECIAL_EDGE

	override fun toString(): String = "$type: $start -> $end"
}
