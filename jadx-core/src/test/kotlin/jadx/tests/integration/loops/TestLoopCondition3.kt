package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套条件判断下的循环条件还原。
 */
class TestLoopCondition3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopCondition3Fixture.TestCls::class.java))
			.code()
			.containsOne("while (a < 12) {")
			.containsOne("if (b + a < 9 && b < 8) {")
			.containsOne("if (b >= 2 && a > -1 && b < 6) {")
	}
}
