package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环中同时存在 continue 与 break 时应正确还原两者。
 */
class TestContinueInLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestContinueInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("for (int i = 0; i < a.length; i++) {")
			.containsOne("if (i < b) {")
			.containsOne("continue;")
			.containsOne("break;")
			.containsOne("this.f++;")
	}
}
