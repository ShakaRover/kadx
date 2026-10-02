package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import org.junit.jupiter.api.Test

/**
 * 多个连续 NOP 指令：加载时不应产生错误。
 */
class TestMultipleNOPs : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()

		// expected no errors
		loadFromSmaliFiles()
	}
}
