package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import org.junit.jupiter.api.Test

/**
 * if 中的三元表达式 3：smali 输入应能成功反编译。
 */
class TestTernaryInIf3 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		getClassNodeFromSmali()
	}
}
