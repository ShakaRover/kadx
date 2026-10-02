package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * switch 各分支直接 return：应消除冗余 return，仅保留 break。
 */
class TestSwitch3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitch3Fixture.TestCls::class.java))
			.code()
			.countString(3, "break;")
			.countString(0, "return;")
	}
}
