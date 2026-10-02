package jadx.tests.integration.loops

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多入口循环应还原为 `while (true)`。
 */
class TestMultiEntryLoop : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("while (true) {")
	}
}
