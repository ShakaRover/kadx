package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * for 循环内嵌 while 循环的还原。
 */
class TestNestedLoops2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestNestedLoops2Fixture.TestCls::class.java))
			.code()
			.containsOne("for (int i = 0; i < list.size(); i++) {")
			.containsOne("while (j < s.length()) {")
	}
}
