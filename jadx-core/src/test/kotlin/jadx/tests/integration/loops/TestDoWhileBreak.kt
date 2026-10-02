package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * do-while 中的 break 应保留 while 结构。
 */
class TestDoWhileBreak : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestDoWhileBreakFixture.TestCls::class.java))
			.code()
			.containsOne("while (")
	}
}
