package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件：`(a && b) || c` 不应被改写成德摩根否定形式。
 */
class TestConditions : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConditionsFixture.TestCls::class.java))
			.code()
			.doesNotContain("(!a || !b) && !c")
			.contains("return (a && b) || c;")
	}
}
