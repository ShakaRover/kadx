package kadx.tests.integration.loops

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 空无限循环，issue #1611。
 */
class TestEndlessLoop2 : SmaliTest() {
	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(2, "while (true) {")
	}
}
