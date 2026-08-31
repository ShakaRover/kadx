package jadx.api.plugins.input.data.annotations

/**
 * 编码值的类型枚举。
 *
 * 定义 Java/Dex 注解值的所有可能类型。这些类型对应 class file 的
 * ConstantPool 和 annotation structure 中的编码格式。
 *
 * **分类**：
 *
 * **基本数值类型**：
 * - BOOLEAN、BYTE、SHORT、CHAR、INT、LONG、FLOAT、DOUBLE
 *
 * **引用类型**：
 * - STRING：字符串字面量
 * - TYPE：类型引用（如 `Ljava/lang/String;`）
 * - ENUM：枚举常量引用
 * - FIELD：字段引用
 * - METHOD：方法引用
 *
 * **复合/特殊类型**：
 * - ARRAY：注解数组
 * - ANNOTATION：嵌套注解
 * - NULL：空值
 *
 * **Java 8+ 特性**：
 * - METHOD_TYPE：方法类型（lambda/method reference）
 * - METHOD_HANDLE：方法句柄（invokedynamic）
 */
public enum class EncodedType(
	/** 人类可读的描述 */
	public val description: String,
) {
	/** null 值 */
	ENCODED_NULL("Null value"),

	/** boolean 类型（true/false）*/
	ENCODED_BOOLEAN("Boolean value"),

	/** byte 类型（-128~127）*/
	ENCODED_BYTE("Byte value"),

	/** short 类型（-32768~32767）*/
	ENCODED_SHORT("Short value"),

	/** char 类型（Unicode 字符）*/
	ENCODED_CHAR("Char value"),

	/** int 类型（32 位整数）*/
	ENCODED_INT("Int value"),

	/** long 类型（64 位整数）*/
	ENCODED_LONG("Long value"),

	/** float 类型（32 位浮点数）*/
	ENCODED_FLOAT("Float value"),

	/** double 类型（64 位浮点数）*/
	ENCODED_DOUBLE("Double value"),

	/** String 类型 */
	ENCODED_STRING("String value"),

	/** 类型引用（class file internal name）*/
	ENCODED_TYPE("Type reference"),

	/** 枚举常量（格式："constantType#name"）*/
	ENCODED_ENUM("Enum constant"),

	/** 字段引用 */
	ENCODED_FIELD("Field reference"),

	/** 方法引用 */
	ENCODED_METHOD("Method reference"),

	/** 方法类型（lambda/method reference descriptor）*/
	ENCODED_METHOD_TYPE("Method type"),

	/** 方法句柄（invokedynamic bootstrap）*/
	ENCODED_METHOD_HANDLE("Method handle"),

	/** 注解数组 */
	ENCODED_ARRAY("Annotation array"),

	/** 嵌套注解 */
	ENCODED_ANNOTATION("Nested annotation"),
}
