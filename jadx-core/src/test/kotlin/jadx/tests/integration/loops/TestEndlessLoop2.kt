package jadx.tests.integration.loops

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
