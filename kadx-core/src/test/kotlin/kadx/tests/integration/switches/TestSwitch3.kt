package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
