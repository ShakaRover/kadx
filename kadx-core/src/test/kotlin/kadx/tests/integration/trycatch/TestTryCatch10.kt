package kadx.tests.integration.trycatch

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try/catch 中应包含 3 个 `return false;`。
 */
class TestTryCatch10 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(3, "return false;")
	}
}
