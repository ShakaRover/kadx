package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环条件中包含布尔变量与比较运算。
 */
class TestLoopCondition2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopCondition2Fixture.TestCls::class.java))
			.code()
			.containsOne("int i = 0;")
			.containsOne("while (a && i < 10) {")
			.containsOne("i++;")
			.containsOne("return i;")
	}
}
