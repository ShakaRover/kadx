package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 15：长串字符串 equals 的或运算链，首尾两端都应完整保留。
 */
class TestConditions15 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions15Fixture.TestCls::class.java))
			.code()
			.containsOne("\"1\".equals(name)")
			.containsOne("\"30\".equals(name)")
	}
}
