package jadx.tests.integration.switches

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
