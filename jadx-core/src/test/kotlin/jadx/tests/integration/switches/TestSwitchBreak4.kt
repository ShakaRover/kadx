package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * case 内 if / else-if / else 三分支，末尾 break：应还原出 else 块。
 */
class TestSwitchBreak4 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA11, TestProfile.D8_J11)
	fun test() {
		assertThat(getClassNode(TestSwitchBreak4Fixture.TestCls::class.java))
			.code()
			.countString(2, "break;")
			.containsOne("} else if (")
			.containsOne("} else {")
	}
}
