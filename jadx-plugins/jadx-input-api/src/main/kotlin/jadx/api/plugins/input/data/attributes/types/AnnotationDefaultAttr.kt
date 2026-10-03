package jadx.api.plugins.input.data.attributes.types

import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 注解默认值属性：存储注解方法（@interface 中的方法）的默认值。
 *
 * **背景**：对应 Java 注解定义里的 `default` 子句，如
 * ```java
 * @interface MyAnn {
 *     float value() default 1.1f;   // ← 这个 1.1f 存在 AnnotationDefaultAttr 里
 * }
 * ```
 *
 * @param value 默认值的编码形式（见 [EncodedValue]）
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getValue()` getter，
 * 这里改用主构造器属性 `val value`，字节码生成的 getter 完全相同。

 * **open 说明**：原 Java 类非 final，jadx-java-input 的 JavaAnnotationDefaultAttr 继承它，故声明为 `open`。
 */
public open class AnnotationDefaultAttr(
	/** 默认值的编码形式 */
	public val value: EncodedValue,
) : PinnedAttribute() {

	/**
	 * 返回本属性的类型标识：[JadxAttrType.ANNOTATION_DEFAULT]。
	 *
	 * 原 Java 声明为 `IJadxAttrType<? extends IJadxAttribute>`，这里用协变的具体类型
	 * `JadxAttrType<AnnotationDefaultAttr>`（Kotlin 允许对 Java 通配符签名做协变覆写）。
	 */
	override val attrType: JadxAttrType<AnnotationDefaultAttr> get() = JadxAttrType.ANNOTATION_DEFAULT

	/** 调试字符串，格式与原 Java 一致：`ANNOTATION_DEFAULT: <值>`（冒号后有空格）*/
	override fun toString(): String = "ANNOTATION_DEFAULT: $value"
}
