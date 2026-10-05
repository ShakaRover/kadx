package kadx.tests.integration.switches

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * issue #2359：字符串 switch 的 case 标签与 default 数量还原。
 */
class TestSwitchOverStrings5 : SmaliTest() {

	@Test
	fun testSmali() {
		disableCompilation()
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("case \"mp3_400_e5\"")
			.countString(9, "case ")
			.countString(1, "default:")
	}
}
