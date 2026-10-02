package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多层 try/finally：提取与不提取 finally 两种模式下的代码形态。
 */
class TestTryCatchFinally12 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally12Fixture.TestCls::class.java))
			.code()
			.countString(3, "} finally {")
	}

	@Test
	fun testWithoutFinally() {
		getArgs().isExtractFinally = false
		assertThat(getClassNode(TestTryCatchFinally12Fixture.TestCls::class.java))
			.code()
			.doesNotContain("} finally {")
			.countString(2 + 2 + 3, "sb.append(\"-finally\");")
	}
}
