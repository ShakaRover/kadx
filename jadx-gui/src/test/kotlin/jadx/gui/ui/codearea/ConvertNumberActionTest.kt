package jadx.gui.ui.codearea

import jadx.gui.utils.NLS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * 数字进制转换测试。
 *
 * **做什么**：校验 [ConvertNumberAction.getConversionsFromWord] 对十进制 / 十六进制 /
 * 二进制 / 字符字面量的解析与格式化结果。
 */
class ConvertNumberActionTest {

	@Test
	fun nonNumeric() {
		assertThat(ConvertNumberAction.getConversionsFromWord("non-numeric")).isNullOrEmpty()
		assertThat(ConvertNumberAction.getConversionsFromWord("0xnon-numeric")).isNullOrEmpty()
		assertThat(ConvertNumberAction.getConversionsFromWord("non-numericL")).isNullOrEmpty()
		assertThat(ConvertNumberAction.getConversionsFromWord("-non-numeric")).isNullOrEmpty()
		assertThat(ConvertNumberAction.getConversionsFromWord("ABCD")).isNullOrEmpty()
	}

	@Test
	fun simpleDecimalToHex() {
		val expected = listOf("0x7b", "0b01111011", "'{'")

		val result = ConvertNumberAction.getConversionsFromWord("123")
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun negativeDecimalToHex() {
		val expected = listOf("0xffffff85", "0b11111111111111111111111110000101")

		val result = ConvertNumberAction.getConversionsFromWord("-123")
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun negativeLongDecimalToHex() {
		// 原 Java 用默认 Locale 的 toLowerCase()，这里用 locale 无关的 lowercase()；
		// 该字符串不含 I，任何 Locale 下结果一致。
		val expected = listOf("0xFFFFFFE8B7891800".lowercase(), "0b1111111111111111111111111110100010110111100010010001100000000000")

		val result = ConvertNumberAction.getConversionsFromWord("-100000000000")
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun simpleHexToDecimal() {
		val expected = listOf("123", "0b01111011", "'{'")

		val result = ConvertNumberAction.getConversionsFromWord("0x7b")
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun zero() {
		val result = ConvertNumberAction.getConversionsFromWord(0.toString())
		assertThat(result).isEmpty()
	}

	@Test
	fun minIntToHex() {
		val expected = listOf("0x80000000", "0b10000000000000000000000000000000")

		val result = ConvertNumberAction.getConversionsFromWord(Int.MIN_VALUE.toString())
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun maxIntToHex() {
		val expected = listOf("0x7fffffff", "0b01111111111111111111111111111111")

		val result = ConvertNumberAction.getConversionsFromWord(Int.MAX_VALUE.toString())
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun minLongToHex() {
		val expected = listOf("0x8000000000000000", "0b1000000000000000000000000000000000000000000000000000000000000000")

		val result = ConvertNumberAction.getConversionsFromWord(Long.MIN_VALUE.toString())
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun maxLongToHex() {
		val expected = listOf("0x7fffffffffffffff", "0b0111111111111111111111111111111111111111111111111111111111111111")

		val result = ConvertNumberAction.getConversionsFromWord(Long.MAX_VALUE.toString())
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun simpleLongSuffix() {
		val expected = listOf("0x7b", "0b01111011", "'{'")

		val result = ConvertNumberAction.getConversionsFromWord("123L")
		assertThat(result).isEqualTo(expected)
	}

	@Test
	fun binaryPadding() {
		assertThat(ConvertNumberAction.getConversionsFromWord("1")).containsOnlyOnce("0b00000001")
		assertThat(ConvertNumberAction.getConversionsFromWord("127")).containsOnlyOnce("0b01111111")
		assertThat(ConvertNumberAction.getConversionsFromWord("0xff")).containsOnlyOnce("0b11111111")
		assertThat(ConvertNumberAction.getConversionsFromWord("0x7fff")).containsOnlyOnce("0b0111111111111111")
		assertThat(ConvertNumberAction.getConversionsFromWord("0xffff")).containsOnlyOnce("0b1111111111111111")
		assertThat(ConvertNumberAction.getConversionsFromWord("0x10000")).containsOnlyOnce("0b000000010000000000000000")
		assertThat(ConvertNumberAction.getConversionsFromWord("0xffffffff")).containsOnlyOnce("0b11111111111111111111111111111111")

		assertThat(ConvertNumberAction.getConversionsFromWord("0xffffffffffff"))
			.containsOnlyOnce("0b111111111111111111111111111111111111111111111111")

		assertThat(ConvertNumberAction.getConversionsFromWord("0x7fffffffffff"))
			.containsOnlyOnce("0b011111111111111111111111111111111111111111111111")

		assertThat(ConvertNumberAction.getConversionsFromWord("0x7fffffffffffffff"))
			.containsOnlyOnce("0b0111111111111111111111111111111111111111111111111111111111111111")
	}

	@Test
	fun printableAscii() {
		for (i in 32 until 127) {
			val printed = String.format("'%c'", i)
			assertThat(ConvertNumberAction.getConversionsFromWord(i.toString())).containsOnlyOnce(printed)
		}
	}

	companion object {
		@BeforeAll
		@JvmStatic
		fun init() {
			NLS.setLocale(NLS.defaultLocale())
		}
	}
}
