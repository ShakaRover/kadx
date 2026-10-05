package kadx.core.dex.instructions

/**
 * 指令类型枚举：kadx 内部对 Dex / class 文件指令的“规范化分类”。
 *
 * 为什么要做这层分类？原始字节码里同一条语义可能由多条 opcode 表示
 * （例如 `const` 与 `const-wide` 都归为 [CONST]），后续的 SSA 变换、类型推断、
 * 控制流恢复和代码生成都统一按 [InsnType] 分派处理，避免到处判断原始 opcode。
 *
 * Kotlin 转换说明：枚举常量名与原 Java 完全一致，Java 调用方仍写 `InsnType.CONST`。
 */
enum class InsnType {
	CONST,
	CONST_STR,
	CONST_CLASS,

	ARITH,
	NEG,
	NOT,

	MOVE,
	MOVE_MULTI,
	CAST,

	RETURN,
	GOTO,

	THROW,
	MOVE_EXCEPTION,

	CMP_L,
	CMP_G,
	IF,
	SWITCH,
	SWITCH_DATA,

	MONITOR_ENTER,
	MONITOR_EXIT,

	CHECK_CAST,
	INSTANCE_OF,

	ARRAY_LENGTH,
	FILL_ARRAY,
	FILL_ARRAY_DATA,
	FILLED_NEW_ARRAY,

	AGET,
	APUT,

	NEW_ARRAY,
	NEW_INSTANCE,

	IGET,
	IPUT,

	SGET,
	SPUT,

	INVOKE,
	MOVE_RESULT,

	// *** 附加指令（kadx 内部生成，不一定直接对应原始 opcode）***

	// 被删除指令的占位
	NOP,

	TERNARY,
	CONSTRUCTOR,

	BREAK,
	CONTINUE,

	// 字符串拼接
	STR_CONCAT,

	// 只生成一个参数
	ONE_ARG,
	PHI,

	// 保留“将在区域代码生成时使用”的参数的伪指令
	REGION_ARG,

	// Java 特有的动态跳转指令
	JAVA_JSR,
	JAVA_RET,
}
