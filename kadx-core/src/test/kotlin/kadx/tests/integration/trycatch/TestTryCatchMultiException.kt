package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多异常 catch 应合并为 `ProviderException | DateTimeException e` 形式。
 */
class TestTryCatchMultiException : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		val catchExcVarName = "e"
		assertThat(getClassNode(TestTryCatchMultiExceptionFixture.TestCls::class.java))
			.code()
			.containsOne("} catch (ProviderException | DateTimeException $catchExcVarName) {")
			.containsOne("throw new RuntimeException($catchExcVarName);")
	}
}
