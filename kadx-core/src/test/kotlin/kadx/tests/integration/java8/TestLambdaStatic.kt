package kadx.tests.integration.java8

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态上下文中的 lambda 与方法引用（含捕获外部变量的场景）应被正确还原。
 */
class TestLambdaStatic : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLambdaStaticFixture.TestCls::class.java))
			.code()
			.doesNotContain("lambda$")
			.doesNotContain("renamed")
			.containsLines(
				2,
				"return () -> {",
				indent() + "return \"test\";",
				"};",
			)
			.containsLines(
				2,
				"return () -> {",
				indent() + "return str;",
				"};",
			)
			.containsOne("return Integer::parseInt;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestLambdaStaticFixture.TestCls::class.java)
	}

	@Test
	fun testFallback() {
		setFallback()
		getClassNode(TestLambdaStaticFixture.TestCls::class.java)
	}
}
