package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * do-while 中提前 return 的还原。
 */
class TestDoWhileBreak2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestDoWhileBreak2Fixture.TestCls::class.java))
			.code()
			.containsLine(2, "do {")
			.containsLine(3, "obj = this.it.next();")
			.containsLine(3, "if (obj == null) {")
			.containsLine(2, "} while (this.it.hasNext());")
	}
}
