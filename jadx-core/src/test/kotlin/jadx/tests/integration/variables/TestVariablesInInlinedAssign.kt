package jadx.tests.integration.variables

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 内联赋值中的变量：`for` 循环中声明于循环外的 `i` 与循环体内的 `c` 都应保留。
 */
class TestVariablesInInlinedAssign : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		assertThat(getClassNode(TestVariablesInInlinedAssignFixture.TestCls::class.java))
			.code()
			.containsOne("char c")
	}
}
