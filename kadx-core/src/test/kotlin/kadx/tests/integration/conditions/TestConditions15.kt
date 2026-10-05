package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 15：长串字符串 equals 的或运算链，首尾两端都应完整保留。
 */
class TestConditions15 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions15Fixture.TestCls::class.java))
			.code()
			.containsOne("\"1\".equals(name)")
			.containsOne("\"30\".equals(name)")
	}
}
