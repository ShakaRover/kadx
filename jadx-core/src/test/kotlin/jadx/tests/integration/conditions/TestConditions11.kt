package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
