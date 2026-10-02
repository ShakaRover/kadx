package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * case 内 if / else-if 链，末尾 break：应还原为 `} else if (`。
 */
class TestSwitchBreak3 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA11, TestProfile.D8_J11)
	fun test() {
		assertThat(getClassNode(TestSwitchBreak3Fixture.TestCls::class.java))
			.code()
			.countString(2, "break;")
			.containsOne("} else if (")
	}
}
