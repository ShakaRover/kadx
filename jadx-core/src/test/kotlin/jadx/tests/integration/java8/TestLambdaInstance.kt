package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 实例 lambda：捕获 `this` 的表达式 lambda 与方法引用都应被正确还原。
 */
class TestLambdaInstance : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLambdaInstanceFixture.TestCls::class.java))
			.code()
			.doesNotContain("lambda$")
			.doesNotContain("renamed")
			.containsLines(
				2,
				"return str -> {",
				indent() + "return call(str);",
				"};",
			)
			// .containsOne("return Object::toString;") // TODO
			.containsOne("return this::call;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestLambdaInstanceFixture.TestCls::class.java)
	}

	@Test
	fun testFallback() {
		setFallback()
		getClassNode(TestLambdaInstanceFixture.TestCls::class.java)
	}
}
