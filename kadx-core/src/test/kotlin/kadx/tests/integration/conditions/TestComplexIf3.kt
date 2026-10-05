package kadx.tests.integration.conditions

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
