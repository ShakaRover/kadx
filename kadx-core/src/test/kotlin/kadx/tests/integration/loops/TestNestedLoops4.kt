package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 三层嵌套 for 循环（带 break 与 return）的还原。
 */
class TestNestedLoops4 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestNestedLoops4Fixture.TestCls::class.java))
			.code()
			.containsOne("break;")
			.containsOne("return 0;")
	}
}
