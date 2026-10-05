package kadx.api.plugins.input.data.attributes.types

import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 注解类默认值属性：存储 @interface 中所有方法的默认值集合。
 *
 * **背景**：与 [AnnotationDefaultAttr]（单个方法）不同，这个属性面向整个注解类型，
 * key 为注解方法名，value 为该方法的默认值编码形式。
 *
 * @param values 方法名 → 默认值的映射
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getValues()` getter，
 * 这里改用主构造器属性 `val values`，字节码生成的 getter 完全相同。
 */
public class AnnotationDefaultClassAttr(
	/** 方法名 → 默认值的映射 */
	public val values: Map<String, EncodedValue>,
) : PinnedAttribute() {

	/**
	 * 返回本属性的类型标识：[KadxAttrType.ANNOTATION_DEFAULT_CLASS]。
	 *
	 * 原 Java 声明为 `IKadxAttrType<? extends IKadxAttribute>`，这里用协变的具体类型
	 * `KadxAttrType<AnnotationDefaultClassAttr>`（Kotlin 允许对 Java 通配符签名做协变覆写）。
	 */
	override val attrType: KadxAttrType<AnnotationDefaultClassAttr> get() = KadxAttrType.ANNOTATION_DEFAULT_CLASS

	/** 调试字符串，格式与原 Java 一致：`ANNOTATION_DEFAULT_CLASS: <映射>`（冒号后有空格）*/
	override fun toString(): String = "ANNOTATION_DEFAULT_CLASS: $values"
}
