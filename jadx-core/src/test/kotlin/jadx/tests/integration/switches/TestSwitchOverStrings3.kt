package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
