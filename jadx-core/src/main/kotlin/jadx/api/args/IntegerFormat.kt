package jadx.api.args

/**
 * 整数字面量的输出格式。
 *
 * 公共 API：jadx-cli / jadx-gui 会把用户选择转换成该枚举，常量名与顺序保持不变。
 */
enum class IntegerFormat {
	/** 由 jadx 自动决定（默认）。 */
	AUTO,

	/** 十进制。 */
	DECIMAL,

	/** 十六进制。 */
	HEXADECIMAL,

	;

	/** 是否为十六进制格式。 */
	fun isHexadecimal(): Boolean = this == HEXADECIMAL
}
