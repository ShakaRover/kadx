@file:Suppress("ktlint:standard:property-naming")

package jadx.core.utils

import jadx.api.JadxArgs
import jadx.api.args.IntegerFormat
import jadx.core.deobf.NameMapper
import jadx.core.utils.exceptions.JadxRuntimeException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Collections
import java.util.Date

/**
 * 字符串处理工具：转义/反转义、按码点遍历、数字格式化等。
 *
 * **实例 vs 静态**：数字格式化依赖 [JadxArgs] 里的整数显示格式与是否转义 Unicode，
 * 因此保留实例状态；其余与配置无关的工具方法保持静态（companion + `@JvmStatic`）。
 */
class StringUtils(args: JadxArgs) {

	private val escapeUnicode: Boolean = args.isEscapeUnicode
	private val integerFormat: IntegerFormat = args.integerFormat

	fun getIntegerFormat(): IntegerFormat = integerFormat

	/**
	 * 把字符串转成带双引号的 Java 字面量形式（处理转义）。
	 */
	fun unescapeString(str: String): String {
		val len = str.length
		if (len == 0) {
			return "\"\""
		}
		val res = StringBuilder()
		res.append('"')
		visitCodePoints(str) { codePoint -> processCodePoint(codePoint, res) }
		res.append('"')
		return res.toString()
	}

	private fun processCodePoint(codePoint: Int, res: StringBuilder) {
		val str = getSpecialStringForCodePoint(codePoint)
		if (str != null) {
			res.append(str)
			return
		}
		if (isEscapeNeededForCodePoint(codePoint)) {
			res.append("\\u").append(String.format("%04x", codePoint))
		} else {
			res.appendCodePoint(codePoint)
		}
	}

	private fun isEscapeNeededForCodePoint(codePoint: Int): Boolean {
		if (codePoint < 32) {
			return true
		}
		if (codePoint < 127) {
			return false
		}
		if (escapeUnicode) {
			return true
		}
		return !NameMapper.isPrintableCodePoint(codePoint)
	}

	/**
	 * 以最合适的方式表示单个字符。
	 */
	fun unescapeChar(c: Char, explicitCast: Boolean): String {
		if (c == '\'') {
			return "'\\''"
		}
		val str = getSpecialStringForCodePoint(c.code)
		if (str != null) {
			return "'$str'"
		}
		if (c.code >= 127 && escapeUnicode) {
			return String.format("'\\u%04x'", c.code)
		}
		if (NameMapper.isPrintableChar(c)) {
			return "'$c'"
		}
		val intStr = c.code.toString()
		return if (explicitCast) "(char) $intStr" else intStr
	}

	fun unescapeChar(ch: Char): String = unescapeChar(ch, false)

	private fun getSpecialStringForCodePoint(c: Int): String? = when (c) {
		'\n'.code -> "\\n"
		'\r'.code -> "\\r"
		'\t'.code -> "\\t"
		'\b'.code -> "\\b"
		'\u000c'.code -> "\\f"
		'\''.code -> "'"
		'"'.code -> "\\\""
		'\\'.code -> "\\\\"
		else -> null
	}

	private fun formatNumber(number: Long, bytesLen: Int, cast0: Boolean): String {
		var cast = cast0
		val numStr: String
		if (integerFormat.isHexadecimal()) {
			val hexStr = java.lang.Long.toHexString(number)
			if (number < 0) {
				// 负数去掉前导 'f'，匹配对应类型的位宽
				val len = hexStr.length
				numStr = "0x" + hexStr.substring(len - bytesLen * 2, len)
				// 无符号负数大于 int/long 有符号上限，必须强制转换
				cast = true
			} else {
				numStr = "0x$hexStr"
			}
		} else {
			numStr = number.toString()
		}
		if (bytesLen == 8 && (number == Long.MIN_VALUE || Math.abs(number) >= Int.MAX_VALUE)) {
			// long 超出 int 范围时强制加 L，避免 “integer number too large”
			cast = true
		}
		if (cast) {
			if (bytesLen == 8) {
				return numStr + 'L'
			}
			return getCastStr(bytesLen) + numStr
		}
		return numStr
	}

	fun formatByte(l: Long, cast: Boolean): String = formatNumber(l, 1, cast)

	fun formatShort(l: Long, cast: Boolean): String {
		if (integerFormat == IntegerFormat.AUTO) {
			when (l.toShort()) {
				Short.MAX_VALUE -> return "Short.MAX_VALUE"
				Short.MIN_VALUE -> return "Short.MIN_VALUE"
			}
		}
		return formatNumber(l, 2, cast)
	}

	fun formatInteger(l: Long, cast: Boolean): String {
		if (integerFormat == IntegerFormat.AUTO) {
			when (l.toInt()) {
				Int.MAX_VALUE -> return "Integer.MAX_VALUE"
				Int.MIN_VALUE -> return "Integer.MIN_VALUE"
			}
		}
		return formatNumber(l, 4, cast)
	}

	fun formatLong(l: Long, cast: Boolean): String {
		if (integerFormat == IntegerFormat.AUTO) {
			if (l == Long.MAX_VALUE) {
				return "Long.MAX_VALUE"
			}
			if (l == Long.MIN_VALUE) {
				return "Long.MIN_VALUE"
			}
		}
		return formatNumber(l, 8, cast)
	}

	companion object {
		private val DEFAULT_INSTANCE = StringUtils(JadxArgs())
		private const val WHITES = " \t\r\n\u000c\b"
		private const val WORD_SEPARATORS = WHITES + "(\")<,>{}=+-*/|[]\\:;'.`~!#^&"

		@JvmStatic
		fun getInstance(): StringUtils = DEFAULT_INSTANCE

		/**
		 * 按 Unicode 码点遍历字符串（正确处理代理对）。
		 */
		@JvmStatic
		fun visitCodePoints(str: String, visitor: (Int) -> Unit) {
			val len = str.length
			var offset = 0
			while (offset < len) {
				val codePoint = str.codePointAt(offset)
				visitor(codePoint)
				offset += Character.charCount(codePoint)
			}
		}

		@JvmStatic
		fun escape(str: String): String {
			val len = str.length
			val sb = StringBuilder(len)
			for (i in 0 until len) {
				val c = str[i]
				when (c) {
					'.', '/', ';', '$', ' ', ',', '<' -> sb.append('_')
					'[' -> sb.append('A')
					']', '>', '?', '*' -> {}
					else -> sb.append(c)
				}
			}
			return sb.toString()
		}

		@JvmStatic
		fun escapeXML(str: String): String {
			val len = str.length
			val sb = StringBuilder(len)
			for (i in 0 until len) {
				val c = str[i]
				val replace = escapeXmlChar(c)
				if (replace != null) {
					sb.append(replace)
				} else {
					sb.append(c)
				}
			}
			return sb.toString()
		}

		@JvmStatic
		fun escapeResValue(str: String): String {
			val len = str.length
			val sb = StringBuilder(len)
			for (i in 0 until len) {
				commonEscapeAndAppend(sb, str[i])
			}
			return sb.toString()
		}

		@JvmStatic
		fun escapeResStrValue(str: String): String {
			val len = str.length
			val sb = StringBuilder(len)
			for (i in 0 until len) {
				val c = str[i]
				when (c) {
					'"' -> sb.append("\\\"")
					'\'' -> sb.append("\\'")
					else -> commonEscapeAndAppend(sb, c)
				}
			}
			return sb.toString()
		}

		private fun escapeXmlChar(c: Char): String? {
			if (c.code <= 0x1F) {
				return "\\" + c.code
			}
			return when (c) {
				'&' -> "&amp;"
				'<' -> "&lt;"
				'>' -> "&gt;"
				'"' -> "&quot;"
				'\'' -> "&apos;"
				'\\' -> "\\\\"
				else -> null
			}
		}

		private fun escapeWhiteSpaceChar(c: Char): String? = when (c) {
			'\n' -> "\\n"
			'\r' -> "\\r"
			'\t' -> "\\t"
			'\b' -> "\\b"
			'\u000c' -> "\\f"
			else -> null
		}

		private fun commonEscapeAndAppend(sb: StringBuilder, c: Char) {
			var replace = escapeWhiteSpaceChar(c)
			if (replace == null) {
				replace = escapeXmlChar(c)
			}
			if (replace != null) {
				sb.append(replace)
			} else {
				sb.append(c)
			}
		}

		@JvmStatic
		fun notEmpty(str: String?): Boolean = str != null && str.isNotEmpty()

		@JvmStatic
		fun isEmpty(str: String?): Boolean = str == null || str.isEmpty()

		@JvmStatic
		fun notBlank(str: String?): Boolean = notEmpty(str) && str!!.trim().isNotEmpty()

		@JvmStatic
		fun countMatches(str: String?, subStr: String?): Int {
			if (str == null || str.isEmpty() || subStr == null || subStr.isEmpty()) {
				return 0
			}
			val subStrLen = subStr.length
			var count = 0
			var idx = 0
			while (true) {
				idx = str.indexOf(subStr, idx)
				if (idx == -1) {
					break
				}
				count++
				idx += subStrLen
			}
			return count
		}

		@JvmStatic
		fun containsChar(str: String, ch: Char): Boolean = str.indexOf(ch) != -1

		@JvmStatic
		fun removeChar(str: String, ch: Char): String {
			val pos = str.indexOf(ch)
			if (pos == -1) {
				return str
			}
			val sb = StringBuilder(str.length)
			var cur = 0
			var next = pos
			while (true) {
				sb.append(str, cur, next)
				cur = next + 1
				next = str.indexOf(ch, cur)
				if (next == -1) {
					sb.append(str, cur, str.length)
					break
				}
			}
			return sb.toString()
		}

		/**
		 * 返回 content 中从 start 到 pos 之间有多少个换行符。
		 */
		@JvmStatic
		fun countLinesByPos(content: String, pos: Int, start: Int): Int {
			if (start >= pos) {
				return 0
			}
			var count = 0
			var tempPos = start
			do {
				tempPos = content.indexOf("\n", tempPos)
				if (tempPos == -1) {
					break
				}
				if (tempPos >= pos) {
					break
				}
				count += 1
				tempPos += 1
			} while (tempPos < content.length)
			return count
		}

		/**
		 * 返回包含 pos 的整行；end 不为 -1 时定位到该位置的所在行。
		 */
		@JvmStatic
		fun getLine(content: String, pos: Int, end0: Int): String {
			if (pos >= content.length) {
				return ""
			}
			var end = end0
			if (end != -1) {
				if (end > content.length) {
					end = content.length - 1
				}
			} else {
				end = pos + 1
			}
			// 定位行首
			var headPos = content.lastIndexOf("\n", pos)
			if (headPos == -1) {
				headPos = 0
			}
			// 定位行尾
			var endPos = content.indexOf("\n", end)
			if (endPos == -1) {
				endPos = content.length
			}
			return content.substring(headPos, endPos)
		}

		@JvmStatic
		fun isWhite(chr: Char): Boolean = WHITES.indexOf(chr) != -1

		@JvmStatic
		fun isWordSeparator(chr: Char): Boolean = WORD_SEPARATORS.indexOf(chr) != -1

		@JvmStatic
		fun splitByFixedString(content: String?, splitStr: String): List<String> {
			if (isEmpty(content)) {
				return Collections.emptyList()
			}
			val parts = ArrayList<String>()
			val splitLen = splitStr.length
			var pos = 0
			val str = checkNotNull(content)
			while (true) {
				val split = str.indexOf(splitStr, pos)
				if (split == -1) {
					parts.add(str.substring(pos))
					return parts
				}
				parts.add(str.substring(pos, split))
				pos = split + splitLen
			}
		}

		@JvmStatic
		fun removeSuffix(str: String, suffix: String): String {
			if (str.endsWith(suffix)) {
				return str.substring(0, str.length - suffix.length)
			}
			return str
		}

		@JvmStatic
		fun getPrefix(str: String, delim: String): String? {
			val idx = str.indexOf(delim)
			if (idx != -1) {
				return str.substring(0, idx)
			}
			return null
		}

		@JvmStatic
		fun getDateText(): String = SimpleDateFormat("HH:mm:ss").format(Date())

		@JvmStatic
		fun formatDouble(d: Double): String {
			if (d.isNaN()) {
				return "Double.NaN"
			}
			if (d == Double.NEGATIVE_INFINITY) {
				return "Double.NEGATIVE_INFINITY"
			}
			if (d == Double.POSITIVE_INFINITY) {
				return "Double.POSITIVE_INFINITY"
			}
			if (d == Double.MIN_VALUE) {
				return "Double.MIN_VALUE"
			}
			if (d == Double.MAX_VALUE) {
				return "Double.MAX_VALUE"
			}
			if (d == java.lang.Double.MIN_NORMAL) {
				return "Double.MIN_NORMAL"
			}
			return d.toString() + 'd'
		}

		@JvmStatic
		fun formatFloat(f: Float): String {
			if (f.isNaN()) {
				return "Float.NaN"
			}
			if (f == Float.NEGATIVE_INFINITY) {
				return "Float.NEGATIVE_INFINITY"
			}
			if (f == Float.POSITIVE_INFINITY) {
				return "Float.POSITIVE_INFINITY"
			}
			if (f == Float.MIN_VALUE) {
				return "Float.MIN_VALUE"
			}
			if (f == Float.MAX_VALUE) {
				return "Float.MAX_VALUE"
			}
			if (f == java.lang.Float.MIN_NORMAL) {
				return "Float.MIN_NORMAL"
			}
			return f.toString() + 'f'
		}

		@JvmStatic
		fun capitalizeFirstChar(str: String): String {
			if (isEmpty(str)) {
				return str
			}
			return Character.toUpperCase(str[0]) + str.substring(1)
		}

		private fun getCastStr(bytesLen: Int): String = when (bytesLen) {
			1 -> "(byte) "
			2 -> "(short) "
			4 -> "(int) "
			8 -> "(long) "
			else -> throw JadxRuntimeException("Unexpected number type length: $bytesLen")
		}
	}
}
