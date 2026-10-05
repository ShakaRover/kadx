package kadx.tests.integration.types

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 局部变量类型推断：不应把 `String` 变量错误地还原成 `Object string2`，也不应残留 `r1v2` 寄存器名。
 */
class TestTypeResolver5 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("Object string2")
			.doesNotContain("r1v2")
	}
}
