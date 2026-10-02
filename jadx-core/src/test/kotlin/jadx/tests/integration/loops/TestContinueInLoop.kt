package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环中同时存在 continue 与 break 时应正确还原两者。
 */
class TestContinueInLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestContinueInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("for (int i = 0; i < a.length; i++) {")
			.containsOne("if (i < b) {")
			.containsOne("continue;")
			.containsOne("break;")
			.containsOne("this.f++;")
	}
}
