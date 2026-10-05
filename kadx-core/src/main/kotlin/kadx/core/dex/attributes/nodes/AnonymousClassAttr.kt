package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.PinnedAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.ClassNode

/**
 * 匿名类属性：记录某个类是由匿名内部类还原而来，以及它的外部类和基类。
 *
 * **内联方式**（[InlineType]）：
 * - [InlineType.CONSTRUCTOR]：通过构造器调用内联（`new Runnable() { ... }`）；
 * - [InlineType.INSTANCE_FIELD]：作为实例字段内联。
 *
 * **Kotlin 转换说明**：继承常驻属性 [PinnedAttribute]；属性名与 Java getter 一一对应
 * （`outerCls`→`getOuterCls` 等），Java 调用方零改动。
 */
class AnonymousClassAttr(
	val outerCls: ClassNode,
	val baseType: ArgType,
	val inlineType: InlineType,
) : PinnedAttribute() {

	/** 匿名类的内联方式 */
	enum class InlineType {
		CONSTRUCTOR,
		INSTANCE_FIELD,
	}

	override val attrType: AType<AnonymousClassAttr> get() = AType.ANONYMOUS_CLASS

	override fun toString(): String = "AnonymousClass{" + outerCls + ", base: " + baseType + ", inline type: " + inlineType + '}'
}
