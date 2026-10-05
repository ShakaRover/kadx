package kadx.tests.integration.synchronize

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * synchronized 块内联后允许产生代码警告：`1 != 0` 与 `System.gc()` 都应保留。
 */
class TestSynchronized5 : SmaliTest() {
	@Test
	fun test() {
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.contains("1 != 0")
			.contains("System.gc();")
	}
}
