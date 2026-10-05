package kadx.tests.integration.arith

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字段自增/自减与字符串追加：`instanceField++`、`staticField--`、`result += ...`。
 */
class TestFieldIncrement : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldIncrementFixture.TestCls::class.java))
			.code()
			.contains("instanceField++;")
			.contains("staticField--;")
			.contains("result += s + '_';")
	}
}
