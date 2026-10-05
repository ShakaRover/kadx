package kadx.tests.integration.others

import kadx.tests.api.RaungTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Java 11 字符串拼接：`invokedynamic` 的 `makeConcatWithConstants` 应还原为 `+` 表达式。
 */
class TestStringConcatJava11 : RaungTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromRaung())
			.code()
			.containsOne("return str + \"test\";")
			.containsOne("return str + \"test\" + str + 7;")
	}

	@Test
	fun testJava8() {
		noDebugInfo()
		assertThat(getClassNode(TestStringConcatJava11Fixture.TestCls::class.java))
			.code()
			.containsOne("return str + \"test\";")
			.containsOneOf(
				"return str + \"test\" + str + 7;",
				"return str + \"test\" + str + \"7\";",
			) // 动态拼接会把常量加入字符串配方
	}

	@TestWithProfiles(TestProfile.D8_J11, TestProfile.JAVA11)
	fun testJava11() {
		noDebugInfo()
		assertThat(getClassNode(TestStringConcatJava11Fixture.TestCls::class.java))
			.code()
			.containsOne("return str + \"test\";")
			.containsOne("return str + \"test\" + str + \"7\";")
	}
}
