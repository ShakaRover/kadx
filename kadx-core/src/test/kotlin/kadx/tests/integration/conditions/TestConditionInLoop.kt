package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环中的条件：for 循环内的 if/else 应被完整还原；无调试信息时循环可能退化为 while。
 */
class TestConditionInLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditionInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("for (int i = a; i < b; i++) {")
			.containsOne("c += 2;")
			.containsOne("c *= 2;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestConditionInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("while")
	}
}
