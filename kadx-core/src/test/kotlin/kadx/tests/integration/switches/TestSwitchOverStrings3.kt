package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字符串 switch 嵌套在 default 分支内。
 */
class TestSwitchOverStrings3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchOverStrings3Fixture.TestCls::class.java))
			.code()
			.countString(3, "case ")
			.countString(2, "default:")
			.countString(4, "return ")
	}
}
