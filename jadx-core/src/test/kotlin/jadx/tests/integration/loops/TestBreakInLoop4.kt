package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环内嵌套 if 的 break 应还原为 for 而不是 while。
 */
class TestBreakInLoop4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestBreakInLoop4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("while")
			.containsOne("for")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBreakInLoop4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("while")
			.containsOne("for")
	}
}
