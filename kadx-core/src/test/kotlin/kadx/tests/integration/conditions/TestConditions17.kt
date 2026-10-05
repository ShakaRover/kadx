package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 17：`(a & SOMETHING) != 0` 是真正的位运算，不应被转换为逻辑运算。
 */
class TestConditions17 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions17Fixture.TestCls::class.java))
			.code()
			.containsOne(" & ")
	}
}
