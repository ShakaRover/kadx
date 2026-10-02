package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
