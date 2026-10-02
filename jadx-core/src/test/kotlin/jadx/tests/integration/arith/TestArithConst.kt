package jadx.tests.integration.arith

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 测试：常量字段参与算术时应还原为 `i + CONST_INT`，而不是内联常量值。
 */
class TestArithConst : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNodeFromSmaliWithPath("arith", "TestArithConst"))
			.code()
			.containsOne("return i + CONST_INT;")
	}
}
