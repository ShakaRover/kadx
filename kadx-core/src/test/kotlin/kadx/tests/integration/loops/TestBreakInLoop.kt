package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * for 循环中的 break 应还原为 if + break，而不是 else 分支。
 */
class TestBreakInLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestBreakInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("for (int i = 0; i < a.length; i++) {")
			.containsOne("if (i < b) {")
			.containsOne("break;")
			.containsOne("this.f++;")
			// .containsOne("a[i]++;")
			.countString(0, "else")
	}
}
