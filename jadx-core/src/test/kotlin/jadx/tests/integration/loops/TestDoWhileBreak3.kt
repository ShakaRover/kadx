package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
