package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 16：`||` 与 `&&` 混合时应按运算符优先级插入括号，保持语义不变。
 */
class TestConditions16 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions16Fixture.TestCls::class.java))
			.code()
			.containsOne("return a < 0 || (b % 2 != 0 && a > 28) || b < 0;")
	}
}
