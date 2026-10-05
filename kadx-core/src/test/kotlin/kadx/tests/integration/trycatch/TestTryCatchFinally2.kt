package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * try/finally 中的多组 for 循环：finally 的 close 调用应保留，循环变量重命名符合预期。
 */
class TestTryCatchFinally2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatchFinally2Fixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.containsOne("out.close();")
			.containsOne("for (ArgType parent : parents) {")
			.containsOne("for (ClspClass cls : this.classes) {")
			.containsOne("for (ClspClass cls2 : this.classes) {")
	}
}
