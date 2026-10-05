package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环内嵌套 if 的 break 应还原为 for 而不是 while。
 */
class TestBreakInLoop4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestBreakInLoop4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("while")
			.containsOne("for")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBreakInLoop4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("while")
			.containsOne("for")
	}
}
