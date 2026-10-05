package kadx.tests.integration.types

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import kadx.tests.api.utils.assertj.KadxCodeAssertions
import org.junit.jupiter.api.Test

/**
 * 类型推断死循环的回归测试：只要求不再发生栈溢出（`t = obj;` 或 `t = (T) obj;` 均可接受）。
 */
class TestTypeResolver25 : SmaliTest() {

	@Test
	fun testSmali() {
		// TODO: type inference error not yet resolved
		// Check that no stack overflow in type inference for now
		allowWarnInCode()
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.oneOf(
				{ c: KadxCodeAssertions -> c.containsOne("t = obj;") },
				{ c: KadxCodeAssertions -> c.containsOne("t = (T) obj;") },
			)
	}
}
