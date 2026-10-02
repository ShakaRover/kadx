package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * `super.toString()` 调用应保留（不同输入格式均验证）。
 */
class TestSuperInvoke2 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestSuperInvoke2Fixture.TestCls::class.java))
			.code()
			.containsOne("return super.toString();")
	}
}
