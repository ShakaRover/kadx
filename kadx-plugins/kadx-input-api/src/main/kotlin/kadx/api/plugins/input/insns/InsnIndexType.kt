package kadx.api.plugins.input.insns

/**
 * 指令索引的类型枚举。
 *
 * 在反编译过程中，每条指令（Instruction）可能引用各种常量池条目或符号引用。
 * 这个枚举分类了指令中索引字段所指向的目标类型：
 *
 * - NONE：无索引（指令不引用任何外部实体）
 * - TYPE_REF：类型引用（如 new、checkcast 指令中的类类型）
 * - STRING_REF：字符串引用（如 const-string 指令中的字面量）
 * - FIELD_REF：字段引用（如 get-field/put-field 指令中的目标字段）
 * - METHOD_REF：方法引用（如 invoke-virtual/invoke-static 指令中的目标方法）
 * - CALL_SITE：调用站点引用（invokedynamic 指令中的 bootstrap method）
 */
public enum class InsnIndexType(
	/** 人类可读的描述，用于日志和调试输出 */
	public val description: String,
) {
	/** 无索引类型 */
	NONE("No index"),

	/** 类型引用（类、接口等）*/
	TYPE_REF("Type reference"),

	/** 字符串常量引用 */
	STRING_REF("String constant reference"),

	/** 字段符号引用 */
	FIELD_REF("Field reference"),

	/** 方法符号引用 */
	METHOD_REF("Method reference"),

	/** 调用站点（invokedynamic bootstrap）*/
	CALL_SITE("Call site reference"),
}
