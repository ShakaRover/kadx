package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套三元表达式的还原：`compareTo` 结果为 0 时的长度比较分支。
 */
class TestTypeResolver3 : IntegrationTest() {

	@Test
	fun test() {
		useJavaInput()
		assertThat(getClassNode(TestTypeResolver3Fixture.TestCls::class.java))
			.code()
			.containsOneOf(
				"return s1.length() == s2.length() ? 0 : s1.length() < s2.length() ? -1 : 1;",
				"return s1.length() < s2.length() ? -1 : 1;",
			)
	}

	@Test
	fun test2() {
		noDebugInfo()
		getClassNode(TestTypeResolver3Fixture.TestCls::class.java)
	}
}
