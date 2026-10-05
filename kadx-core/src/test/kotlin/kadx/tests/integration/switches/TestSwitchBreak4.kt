package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
