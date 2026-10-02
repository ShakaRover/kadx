package jadx.tests.integration.arith

import jadx.api.args.IntegerFormat
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 数字字面量格式：AUTO / DECIMAL / HEXADECIMAL 三种输出格式下，
 * byte/short/int/long 的极值应正确呈现。
 */
class TestNumbersFormat : IntegrationTest() {

	@Test
	fun test() {
		getArgs().integerFormat = IntegerFormat.AUTO
		assertThat(getClassNode(TestNumbersFormatFixture.TestCls::class.java))
			.code()
			.containsOne("new byte[]{0, -1, -10, -1, -128, 127}")
			.containsOne("new short[]{0, -1, -10, -1, Short.MIN_VALUE, Short.MAX_VALUE}")
			.containsOne("new int[]{0, -1, -10, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}")
			.containsOne("new long[]{0, -1, -10, -1, Long.MIN_VALUE, Long.MAX_VALUE}")
	}

	@Test
	fun testDecimalFormat() {
		getArgs().integerFormat = IntegerFormat.DECIMAL
		assertThat(getClassNode(TestNumbersFormatFixture.TestCls::class.java))
			.code()
			.containsOne("new byte[]{0, -1, -10, -1, -128, 127}")
			.containsOne("new short[]{0, -1, -10, -1, -32768, 32767}")
			.containsOne("new int[]{0, -1, -10, -1, -2147483648, 2147483647}")
			.containsOne("new long[]{0, -1, -10, -1, -9223372036854775808L, 9223372036854775807L}")
	}

	@Test
	fun testHexFormat() {
		getArgs().integerFormat = IntegerFormat.HEXADECIMAL
		assertThat(getClassNode(TestNumbersFormatFixture.TestCls::class.java))
			.code()
			.containsOne("new byte[]{0x0, (byte) 0xff, (byte) 0xf6, (byte) 0xff, (byte) 0x80, 0x7f}")
			.containsOne("new short[]{0x0, (short) 0xffff, (short) 0xfff6, (short) 0xffff, (short) 0x8000, 0x7fff}")
			.containsOne("new int[]{0x0, (int) 0xffffffff, (int) 0xfffffff6, (int) 0xffffffff, (int) 0x80000000, 0x7fffffff}")
			.containsOne(
				"new long[]{0x0, 0xffffffffffffffffL, 0xfffffffffffffff6L, 0xffffffffffffffffL, 0x8000000000000000L, 0x7fffffffffffffffL}",
			)
	}
}
