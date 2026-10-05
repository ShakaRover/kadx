package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
