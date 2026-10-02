package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import jadx.tests.api.utils.assertj.JadxCodeAssertions
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
				{ c: JadxCodeAssertions -> c.containsOne("t = obj;") },
				{ c: JadxCodeAssertions -> c.containsOne("t = (T) obj;") },
			)
	}
}
