package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字符串 switch：case 落空合并到 default。
 */
class TestSwitchOverStrings2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchOverStrings2Fixture.TestCls::class.java))
			.code()
			.countString(4, "case ")
			.countString(1, "default:")
			.countString(2, "return ")
	}
}
