package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 常量字符串拼接：StringBuilder 链与字面量拼接应被优化为可读的字符串表达式。
 */
class TestConstStringConcat : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestConstStringConcatFixture.TestCls::class.java))
			.code()
			.containsOne("return \"Value equals \" + ")
			.containsOne("return \"App version: 1.2\";")
			.containsOne("return \"value \" + str + \" = \" + i;")
	}
}
