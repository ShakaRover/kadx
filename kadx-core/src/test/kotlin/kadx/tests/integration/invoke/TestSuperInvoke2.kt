package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
