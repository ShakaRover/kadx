package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
