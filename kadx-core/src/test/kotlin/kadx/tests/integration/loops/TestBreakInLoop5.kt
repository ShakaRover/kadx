package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环中带多个 break 出口（含 multiplier 变量）的反编译不应抛异常。
 */
class TestBreakInLoop5 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestBreakInLoop5Fixture.TestCls::class.java))
			.code()
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBreakInLoop5Fixture.TestCls::class.java))
			.code()
	}
}
