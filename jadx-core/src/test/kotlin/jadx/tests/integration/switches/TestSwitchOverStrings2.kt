package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
