package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 拆箱与条件表达式：Boolean/Float 三元表达式的拆箱结果不应出现 `boolean valueOf`。
 */
class TestDeboxing5 : IntegrationTest() {

	@TestWithProfiles(TestProfile.D8_J11)
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestDeboxing5Fixture.TestCls::class.java))
			.code()
			.doesNotContain("boolean valueOf")
	}
}
