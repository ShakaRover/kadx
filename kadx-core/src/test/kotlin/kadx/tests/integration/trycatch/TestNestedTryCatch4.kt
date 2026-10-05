package kadx.tests.integration.trycatch

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套 try/catch 不应产生无法推断的类型占位符（`??`）。
 */
class TestNestedTryCatch4 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("?? ")
	}
}
