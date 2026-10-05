package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 11：单个 if 调用方法，不应生成 return 或 else 分支。
 */
class TestConditions11 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConditions11Fixture.TestCls::class.java))
			.code()
			.containsOne("if (a || b > 2) {")
			.containsOne("f();")
			.doesNotContain("return")
			.doesNotContain("else")
	}
}
