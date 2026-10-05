package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 死代码块引用起始位置：不应产生任何 throw 语句。
 */
class TestDeadBlockReferencesStart : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(0, "throw")
	}
}
