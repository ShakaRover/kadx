package jadx.plugins.input.java.data.code

import jadx.plugins.input.java.utils.JavaClassParseException

/**
 * newarray 指令的类型参数解码器。
 *
 **做什么**：newarray 的操作数不是常量池索引而是类型编号（4=boolean…11=long），
 * 本类把它还原成描述符单字符；非法值直接抛异常。
 */
object ArrayType {

	@JvmStatic
	fun byValue(value: Int): String = when (value) {
		4 -> "Z"
		5 -> "C"
		6 -> "F"
		7 -> "D"
		8 -> "B"
		9 -> "S"
		10 -> "I"
		11 -> "J"
		else -> throw JavaClassParseException("Unknown array type value: " + value)
	}
}
