package jadx.tests.integration.variables

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 变量合并：带调试信息时保留 `store` 变量名，
 * 无调试信息时退化为 `obj2`。
 */
class TestVariables2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestVariables2Fixture.TestCls::class.java))
			.code()
			.contains("Object store = s != null ? s : null;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestVariables2Fixture.TestCls::class.java))
			.code()
			.contains("Object obj2 = obj != null ? obj : null;")
	}
}
