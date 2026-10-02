package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环中带多个 break 出口（含 multiplier 变量）的反编译不应抛异常。
 */
class TestBreakInLoop5 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestBreakInLoop5Fixture.TestCls::class.java))
			.code()
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBreakInLoop5Fixture.TestCls::class.java))
			.code()
	}
}
