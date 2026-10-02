package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 循环条件里调用方法（do-while 还原）。
 */
class TestLoopConditionInvoke : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopConditionInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("do {")
			.containsOne("if (ch == 0) {")
			.containsOne("this.pos = startPos;")
			.containsOne("return false;")
			.containsOne("} while (ch != lastChar);")
			.containsOne("return true;")
	}
}
