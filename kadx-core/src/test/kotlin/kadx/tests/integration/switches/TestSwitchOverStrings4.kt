package kadx.tests.integration.switches

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * issue #2770：字符串 switch 的 case 标签应还原为字符串常量。
 */
class TestSwitchOverStrings4 : SmaliTest() {

	@Test
	fun testSmali() {
		disableCompilation()
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("case \"DESKTOP\"")
			.countString(4, "case")
	}
}
