package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 顺序排列的两个 while 循环（加减常量）。
 */
class TestLoopCondition4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopCondition4Fixture.TestCls::class.java))
			.code()
			.containsOne("int n = -1;")
			.containsOne("while (n < 0) {")
			.containsOne("n += 12;")
			.containsOne("while (n > 11) {")
			.containsOne("n -= 12;")
			.containsOne("System.out.println(n);")
	}
}
