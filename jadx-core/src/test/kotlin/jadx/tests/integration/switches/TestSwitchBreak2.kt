package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * case 内 if/else 均不 break，末尾统一 break：检查 break 数量。
 */
class TestSwitchBreak2 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA11, TestProfile.D8_J11)
	fun test() {
		assertThat(getClassNode(TestSwitchBreak2Fixture.TestCls::class.java))
			.code()
			.countString(2, "break;")
	}
}
