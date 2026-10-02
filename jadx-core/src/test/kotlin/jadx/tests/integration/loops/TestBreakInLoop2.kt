package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环 + try/catch 中 break/throw 的还原，验证各分支语句均被保留。
 */
class TestBreakInLoop2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestBreakInLoop2Fixture.TestCls::class.java))
			.code()
			.containsOne("while (true) {")
			.containsOneOf("break;", "return;")
			.containsOne("throw ex;")
			.containsOne("data.clear();")
			.containsOne("Thread.sleep(100L);")
	}
}
