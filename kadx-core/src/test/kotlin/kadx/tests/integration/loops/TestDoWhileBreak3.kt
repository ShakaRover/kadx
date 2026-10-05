package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * do-while 中带 break 的条件应合并进 while 条件。
 */
class TestDoWhileBreak3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestDoWhileBreak3Fixture.TestCls::class.java))
			.code()
			.containsOne("while")
			.containsLines(2, "while (this.it.hasNext() && this.it.next() != null) {", "}")
	}
}
