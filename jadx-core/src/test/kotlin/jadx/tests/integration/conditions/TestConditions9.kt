package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 9：`!a || (b >= 0 && b <= 11)` 的短路组合应完整还原为 if/else。
 */
class TestConditions9 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions9Fixture.TestCls::class.java))
			.code()
			.containsOne("if (!a || (b >= 0 && b <= 11)) {")
			.containsOne("System.out.println('1');")
			.containsOne("} else {")
			.containsOne("System.out.println('2');")
			.doesNotContain("return;")
	}
}
