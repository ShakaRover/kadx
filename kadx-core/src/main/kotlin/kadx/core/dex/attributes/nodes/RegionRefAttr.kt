package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.IRegion

/**
 * 区域引用属性：把一条指令关联到它所“代表”的区域。
 *
 * **用途**：例如 switch 指令会先构建 [kadx.core.dex.regions.SwitchRegion]，
 * 再把该区域回填到指令上，方便后续优化（如 `SwitchBreakVisitor`）反查。
 *
 * **Kotlin 转换说明**：[region] 声明为只读属性，生成的 `getRegion()` 与原 JVM 方法名一致。
 */
class RegionRefAttr(val region: IRegion) : IKadxAttribute {

	override val attrType: AType<RegionRefAttr> get() = AType.REGION_REF

	override fun toString(): String = "RegionRef:" + region.baseString()
}
