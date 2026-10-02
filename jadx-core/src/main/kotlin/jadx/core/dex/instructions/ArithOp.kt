package jadx.core.dex.instructions

/**
 * 算术 / 位运算运算符枚举。
 *
 * [symbol] 保存该运算符在源码中的写法（如 `"+"`、`">>>"`），代码生成时直接拼接。
 * 注意：Dex 的位运算与算术运算共用同一套 opcode 前缀（如 and-int / add-int），
 * jadx 用本枚举区分具体运算。
 *
 * Kotlin 转换说明：[symbol] 声明为属性，其 JVM getter 名恰好是 `getSymbol()`，
 * 因此 Java 调用方 `op.getSymbol()` 保持可用。
 */
enum class ArithOp(val symbol: String) {
	ADD("+"),
	SUB("-"),
	MUL("*"),
	DIV("/"),
	REM("%"),

	AND("&"),
	OR("|"),
	XOR("^"),

	SHL("<<"),
	SHR(">>"),
	USHR(">>>"),
	;

	/**
	 * 是否为按位运算（AND / OR / XOR）。
	 *
	 * 位运算的结果类型会被修正为布尔 / 窄整型（见 ArithNode 的 fixResultType），
	 * 因为位运算常用于布尔表达式，不能简单当作普通 INT 运算处理。
	 */
	fun isBitOp(): Boolean = when (this) {
		AND, OR, XOR -> true
		else -> false
	}
}
