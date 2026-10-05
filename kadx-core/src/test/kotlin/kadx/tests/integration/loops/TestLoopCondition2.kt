package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环条件中包含布尔变量与比较运算。
 */
class TestLoopCondition2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopCondition2Fixture.TestCls::class.java))
			.code()
			.containsOne("int i = 0;")
			.containsOne("while (a && i < 10) {")
			.containsOne("i++;")
			.containsOne("return i;")
	}
}
