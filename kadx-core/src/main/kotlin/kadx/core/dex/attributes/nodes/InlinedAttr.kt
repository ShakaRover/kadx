package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.ClassNode

/**
 * 已内联类属性：记录某个类是由哪个类内联（inline）进来的。
 *
 * **用途**：类内联优化后，被内联的原始类信息需要保留，便于调试/回退。
 */
class InlinedAttr(val inlineCls: ClassNode) : IKadxAttribute {

	override val attrType: IKadxAttrType<InlinedAttr> get() = AType.INLINED

	override fun toString(): String = "INLINED: $inlineCls"
}
