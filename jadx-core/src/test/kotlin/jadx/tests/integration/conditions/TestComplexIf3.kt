package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 复杂条件 3：多处赋值的控制流应被正确恢复，赋值语句数量保持稳定。
 */
class TestComplexIf3 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(1, "iArr = null;")
			.countString(2, "z = false;")
	}
}
