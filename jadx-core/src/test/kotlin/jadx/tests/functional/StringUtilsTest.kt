package jadx.tests.functional

import jadx.api.JadxArgs
import jadx.core.utils.StringUtils
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字符串/字符转义相关工具方法的行为校验。
 */
class StringUtilsTest {

	private lateinit var stringUtils: StringUtils

	@Test
	@Suppress("AvoidEscapedUnicodeCharacters")
	fun testStringUnescape() {
		val args = JadxArgs()
		args.isEscapeUnicode = true
		stringUtils = StringUtils(args)

		checkStringUnescape("", "")
		checkStringUnescape("'", "'")
		checkStringUnescape("a", "a")
		checkStringUnescape("\n", "\\n")
		checkStringUnescape("\t", "\\t")
		checkStringUnescape("\r", "\\r")
		checkStringUnescape("\b", "\\b")
		checkStringUnescape("\u000c", "\\f")
		checkStringUnescape("\\", "\\\\")
		checkStringUnescape("\"", "\\\"")
		checkStringUnescape("\u1234", "\\u1234")
	}

	private fun checkStringUnescape(input: String, result: String) {
		assertThat(stringUtils.unescapeString(input)).isEqualTo('"' + result + '"')
	}

	@Test
	fun testCharUnescape() {
		stringUtils = StringUtils(JadxArgs())

		checkCharUnescape('a', "a")
		checkCharUnescape(' ', " ")
		checkCharUnescape('\n', "\\n")
		checkCharUnescape('\'', "\\'")

		assertThat(stringUtils.unescapeChar('\u0000')).isEqualTo("0")
	}

	private fun checkCharUnescape(input: Char, result: String) {
		assertThat(stringUtils.unescapeChar(input)).isEqualTo('\'' + result + '\'')
	}

	@Test
	fun testResStrValueEscape() {
		checkResStrValueEscape("line\nnew line", "line\\nnew line")
		checkResStrValueEscape("can't", "can\\'t")
		checkResStrValueEscape("quote\"end", "quote\\\"end")
	}

	private fun checkResStrValueEscape(input: String, result: String) {
		assertThat(StringUtils.escapeResStrValue(input)).isEqualTo(result)
	}
}
