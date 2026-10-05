package kadx.tests.integration.loops

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环还原：smali 输入应保留三个 while 循环。
 */
class TestLoopRestore3 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(3, "while (")
	}
}
