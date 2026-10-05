package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 4：`inRange` 布尔变量参与的算术应还原为三元表达式。
 */
class TestConditions4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions4Fixture.TestCls::class.java))
			.code()
			.contains("num >= 59 && num <= 66")
			.contains("? num + 1 : num;")
			.doesNotContain("else")
	}
}
