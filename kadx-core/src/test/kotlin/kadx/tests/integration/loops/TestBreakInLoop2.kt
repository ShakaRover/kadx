package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环 + try/catch 中 break/throw 的还原，验证各分支语句均被保留。
 */
class TestBreakInLoop2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestBreakInLoop2Fixture.TestCls::class.java))
			.code()
			.containsOne("while (true) {")
			.containsOneOf("break;", "return;")
			.containsOne("throw ex;")
			.containsOne("data.clear();")
			.containsOne("Thread.sleep(100L);")
	}
}
