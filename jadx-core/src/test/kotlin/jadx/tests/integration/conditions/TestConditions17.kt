package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 17：`(a & SOMETHING) != 0` 是真正的位运算，不应被转换为逻辑运算。
 */
class TestConditions17 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions17Fixture.TestCls::class.java))
			.code()
			.containsOne(" & ")
	}
}
