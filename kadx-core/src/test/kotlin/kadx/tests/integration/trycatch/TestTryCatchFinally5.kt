package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try 内 do/while 遍历并提前 return：finally 中的 close 调用应保留。
 */
class TestTryCatchFinally5 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally5Fixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.containsOne("d.close();")
	}
}
