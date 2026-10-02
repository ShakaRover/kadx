package jadx.tests.integration.names

import jadx.tests.api.SmaliTest
import org.junit.jupiter.api.Test

/**
 * 非法字符类名：smali 中可定义 `do-`、`i-f` 这类含 `-` 的类名，加载时不应崩溃。
 */
class TestClassNameWithInvalidChar : SmaliTest() {
	/*
	 * public class do- {}
	 * public class i-f {}
	 */

	@Test
	fun test() {
		loadFromSmaliFiles()
	}

	@Test
	fun testWithDeobfuscation() {
		enableDeobfuscation()
		loadFromSmaliFiles()
	}
}
