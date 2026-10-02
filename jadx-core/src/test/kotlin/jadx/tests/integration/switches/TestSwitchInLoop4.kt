package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * for 循环内 switch 只匹配一个字符，找到分隔符后跳出。
 */
class TestSwitchInLoop4 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11, TestProfile.JAVA11)
	fun test() {
		assertThat(getClassNode(TestSwitchInLoop4Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (c) {")
			.containsOne("break;") // allow replacing second 'break' with 'return'
	}
}
