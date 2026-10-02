package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 带 `return` 的 lambda 代码块应保留语句结构。
 */
class TestLambdaReturn : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8)
	fun test() {
		assertThat(getClassNode(TestLambdaReturnFixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"Function0<Void> f1 = () -> {",
				indent() + "new T2(94L).w();",
				indent() + "return null;",
				"};",
			)
	}

	@TestWithProfiles(TestProfile.D8_J11_DESUGAR)
	fun testLambda() {
		getClassNode(TestLambdaReturnFixture.TestCls::class.java)
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestLambdaReturnFixture.TestCls::class.java)
	}
}
