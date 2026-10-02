package jadx.tests.integration.loops

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
