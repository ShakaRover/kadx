package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 无结果的字符串拼接：拼接结果仅赋给局部变量时，反编译应保留 `+` 表达式。
 */
class TestStringConcatWithoutResult : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestStringConcatWithoutResultFixture.TestCls::class.java))
			.code()
			.containsOne(" = \"Input arg value: \" + i;")
	}
}
