package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 顺序排列的两个 while 循环（加减常量）。
 */
class TestLoopCondition4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopCondition4Fixture.TestCls::class.java))
			.code()
			.containsOne("int n = -1;")
			.containsOne("while (n < 0) {")
			.containsOne("n += 12;")
			.containsOne("while (n > 11) {")
			.containsOne("n -= 12;")
			.containsOne("System.out.println(n);")
	}
}
