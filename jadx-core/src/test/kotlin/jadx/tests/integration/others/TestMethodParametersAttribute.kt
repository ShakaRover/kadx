package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

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
