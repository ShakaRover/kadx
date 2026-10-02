package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 3：多个提前返回（null 检查、集合大小、空串、正则匹配）应还原为连续 if，且不产生 else。
 */
class TestConditions3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConditions3Fixture.TestCls::class.java))
			.code()
			.contains("return null;")
			.doesNotContain("else")
			.doesNotContain("AnonymousClass_1")
	}
}
