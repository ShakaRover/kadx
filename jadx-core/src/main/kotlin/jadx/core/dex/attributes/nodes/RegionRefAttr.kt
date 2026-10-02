package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.IRegion

/**
 * 区域引用属性：把一条指令关联到它所“代表”的区域。
 *
 * **用途**：例如 switch 指令会先构建 [jadx.core.dex.regions.SwitchRegion]，
 * 再把该区域回填到指令上，方便后续优化（如 `SwitchBreakVisitor`）反查。
 *
 * **Kotlin 转换说明**：[region] 声明为只读属性，生成的 `getRegion()` 与原 JVM 方法名一致。
 */
class RegionRefAttr(val region: IRegion) : IJadxAttribute {

	override fun getAttrType(): AType<RegionRefAttr> = AType.REGION_REF

	override fun toString(): String = "RegionRef:" + region.baseString()
}
