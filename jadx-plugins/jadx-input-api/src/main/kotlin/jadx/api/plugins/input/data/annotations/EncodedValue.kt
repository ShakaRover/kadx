package jadx.api.plugins.input.data.annotations

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute
import java.util.Objects

/**
 * 编码值：表示注解属性的值。
 *
 * **背景**：Java 注解的值在 class file/Dex 中以特殊格式编码存储，
 * 这个类封装了这些编码值的类型和实际内容。
 *
 * **支持的类型**（见 [EncodedType]）：
 * - 基本类型：int、long、float、double、boolean、char
 * - 引用类型：String、Class、enum 常量
 * - 复合类型：注解数组、嵌套注解
 * - 特殊值：null
 *
 * **示例**：
 * ```java
 * @MyAnnotation(value = "hello", count = 42, enabled = true)
 * ```
 * 对应三个 EncodedValue：
 * - EncodedType.STRING, value="hello"
 * - EncodedType.INT, value=42
 * - EncodedType.BOOLEAN, value=true
 *
 * **继承 PinnedAttribute**：注解值在反编译过程中频繁访问，固定保留避免重复解析。
 *
 * **Kotlin 转换说明（重要）**：
 * 原 Java 用 `private final` 字段 + `getType()`/`getValue()` 方法。这里改用 Kotlin
 * 主构造器公开属性 `val type` / `val value`，编译器会自动生成与 Java getter 完全相同
 * 的字节码方法 `getType()` / `getValue()`：
 * - Java 调用方继续用 `ev.getType()` / `ev.getValue()`（含方法引用 `EncodedValue::getValue`）—— 零改动；
 * - Kotlin 调用方可用属性语法 `ev.type` / `ev.value`，以及属性引用 `EncodedValue::value`
 *   （Kotlin 中属性引用必须写属性名而非 getter 名）。
 * 因此两种语言、两种风格都能无缝使用，公共 API 保持不变。
 */
public class EncodedValue(
	/** 值的编码类型（见 [EncodedType]）*/
	public val type: EncodedType,
	/** 实际的值对象，具体类型由 [type] 决定 */
	public val value: Any?,
) : PinnedAttribute() {

	companion object {
		/**
		 * null 值的单例实例。
		 *
		 * 用于表示注解属性被显式设置为 null，或可选值未提供。
		 */
		@JvmField
		public val NULL: EncodedValue = EncodedValue(EncodedType.ENCODED_NULL, "null")
	}

	/**
	 * 判断两个编码值是否相等：类型相同且值相等（null 安全）。
	 *
	 * 与原 Java 的 `Objects.equals(value, that.getValue())` 语义一致。
	 */
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is EncodedValue) return false
		return type == other.type && Objects.equals(value, other.value)
	}

	/**
	 * 返回 CONSTANT_VALUE 属性类型。
	 *
	 * EncodedValue 用作 Java constant_value attribute 的数据载体，
	 * 存储字段的编译时常量值（如 `public static final int MAX = 100`）。
	 */
	override fun getAttrType(): IJadxAttrType<*> = JadxAttrType.CONSTANT_VALUE

	/**
	 * 与原 Java 的 `Objects.hash(getType(), getValue())` 完全一致。
	 */
	override fun hashCode(): Int = Objects.hash(type, value)

	/**
	 * 返回值的字符串表示，用于调试和日志。
	 *
	 * **输出格式示例**：
	 * - null → "null"
	 * - 数组 → "[val1, val2, ...]"
	 * - 字符串 → "{STRING: \"hello\"}"
	 * - 其他 → "{TYPE: value}"（type 去掉 "ENCODED_" 前缀）
	 */
	override fun toString(): String = when (type) {
		EncodedType.ENCODED_NULL -> "null"
		EncodedType.ENCODED_ARRAY -> "[$value]"
		EncodedType.ENCODED_STRING -> "{STRING: \"$value\"}"
		else -> "{" + type.toString().substring(8) + ": $value}"
	}
}
