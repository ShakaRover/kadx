package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.ClassNode

/**
 * 已内联类属性：记录某个类是由哪个类内联（inline）进来的。
 *
 * **用途**：类内联优化后，被内联的原始类信息需要保留，便于调试/回退。
 */
class InlinedAttr(val inlineCls: ClassNode) : IJadxAttribute {

	override fun getAttrType(): IJadxAttrType<InlinedAttr> = AType.INLINED

	override fun toString(): String = "INLINED: $inlineCls"
}
