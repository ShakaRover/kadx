package kadx.tests.integration.jbc

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * JBC `dup2_x1`：`this.value = v` 的赋值表达式应正确还原。
 */
class TestDup2x1 : IntegrationTest() {

	@TestWithProfiles(TestProfile.JAVA11)
	fun test() {
		assertThat(getClassNode(TestDup2x1Fixture.TestCls::class.java))
			.code()
			.containsOne("this.value = v;")
	}
}
