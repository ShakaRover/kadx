package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多重 catch（多异常类型）在有无调试信息时均应正确还原。
 */
class TestMultiExceptionCatch2 : IntegrationTest() {

	@Test
	fun test() {
		commonChecks()
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		commonChecks()
	}

	private fun commonChecks() {
		KadxAssertions.assertThat(getClassNode(TestMultiExceptionCatch2Fixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("} catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {")
			.containsOne("e.printStackTrace();")

		// TODO: store vararg attribute for methods from classpath
		// assertThat(code, containsOne("constructor.newInstance();"));
	}
}
