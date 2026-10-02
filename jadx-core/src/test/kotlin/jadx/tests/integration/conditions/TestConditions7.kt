package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 7：数组边界检查的 `&&` 组合应保持原样，不应被改写为 `||`。
 */
class TestConditions7 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions7Fixture.TestCls::class.java))
			.code()
			.contains("if (i >= 0 && i < a.length) {")
			.doesNotContain("||")
	}
}
