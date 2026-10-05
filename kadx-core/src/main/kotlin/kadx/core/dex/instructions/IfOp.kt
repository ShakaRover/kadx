package kadx.core.dex.instructions

import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * 条件跳转的比较运算符枚举（if-eq / if-lt 等）。
 *
 * [symbol] 是对应源码写法，代码生成时用于还原 `a < b` 这样的表达式。
 *
 * Kotlin 转换说明：[symbol] 用属性表示，JVM getter 名就是 `getSymbol()`。
 */
enum class IfOp(val symbol: String) {
	EQ("=="),
	NE("!="),
	LT("<"),
	LE("<="),
	GT(">"),
	GE(">="),
	;

	/**
	 * 返回相反的比较运算符（用于条件取反）。
	 *
	 * 例如 `a == b` 取反得到 `a != b`；`a < b` 取反得到 `a >= b`。
	 */
	fun invert(): IfOp = when (this) {
		EQ -> NE
		NE -> EQ
		LT -> GE
		LE -> GT
		GT -> LE
		GE -> LT
		else -> throw KadxRuntimeException("Unknown if operations type: $this")
	}
}
