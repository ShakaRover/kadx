package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 9：`!a || (b >= 0 && b <= 11)` 的短路组合应完整还原为 if/else。
 */
class TestConditions9 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions9Fixture.TestCls::class.java))
			.code()
			.containsOne("if (!a || (b >= 0 && b <= 11)) {")
			.containsOne("System.out.println('1');")
			.containsOne("} else {")
			.containsOne("System.out.println('2');")
			.doesNotContain("return;")
	}
}
