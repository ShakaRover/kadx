package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 5：null 判断与 equals 的组合应还原为 `if / else if`，不应出现反向的 `if (a1.equals(a2))`。
 */
class TestConditions5 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions5Fixture.TestCls::class.java))
			.code()
			.contains("if (a1 == null) {")
			.contains("if (a2 != null) {")
			.contains("throw new AssertionError(a1 + \" != \" + a2);")
			.doesNotContain("if (a1.equals(a2)) {")
			.contains("} else if (!a1.equals(a2)) {")
	}
}
