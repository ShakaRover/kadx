package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 7：数组边界检查的 `&&` 组合应保持原样，不应被改写为 `||`。
 */
class TestConditions7 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions7Fixture.TestCls::class.java))
			.code()
			.contains("if (i >= 0 && i < a.length) {")
			.doesNotContain("||")
	}
}
