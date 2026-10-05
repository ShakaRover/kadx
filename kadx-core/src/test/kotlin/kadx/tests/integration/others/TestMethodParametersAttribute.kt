package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * MethodParameters 属性：`-parameters` 编译时应保留参数名与 final 修饰符。
 */
class TestMethodParametersAttribute : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA8, TestProfile.D8_J11)
	fun test() {
		getCompilerOptions().addArgument("-parameters")
		noDebugInfo()
		assertThat(getClassNode(TestMethodParametersAttributeFixture.TestCls::class.java))
			.code()
			.containsOne("public String test(String paramStr, final int number) {")
	}
}
