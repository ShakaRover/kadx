package jadx.tests.integration.variables

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 异常与变量组合：`InvocationTargetException` 分支、`exc = e.getCause()`
 * 以及字符串拼接的异常信息都应被正确还原。
 */
class TestVariables4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestVariables4Fixture.TestCls::class.java))
			.code()
			.contains("} catch (InvocationTargetException e) {")
			.contains("pass = false;")
			.contains("exc = e.getCause();")
			.contains("System.err.println(\"Class '\" + clsName + \"' not found\");")
			.contains("return pass;")
	}

	@Test
	fun test2() {
		noDebugInfo()
		getClassNode(TestVariables4Fixture.TestCls::class.java)
	}
}
