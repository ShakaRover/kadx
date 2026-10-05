package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环条件里调用方法（do-while 还原）。
 */
class TestLoopConditionInvoke : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopConditionInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("do {")
			.containsOne("if (ch == 0) {")
			.containsOne("this.pos = startPos;")
			.containsOne("return false;")
			.containsOne("} while (ch != lastChar);")
			.containsOne("return true;")
	}
}
