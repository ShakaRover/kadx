package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * do-while 中的 break 应保留 while 结构。
 */
class TestDoWhileBreak : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestDoWhileBreakFixture.TestCls::class.java))
			.code()
			.containsOne("while (")
	}
}
