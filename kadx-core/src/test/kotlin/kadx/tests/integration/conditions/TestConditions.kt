package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
