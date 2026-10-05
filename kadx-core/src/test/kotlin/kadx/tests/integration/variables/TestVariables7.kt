package kadx.tests.integration.variables

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 测试：无调试信息时寄存器变量也不应直接输出 `r0`。
 */
class TestVariables7 : SmaliTest() {

	@Test
	fun testNoDebug() {
		getArgs().isDebugInfo = false
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("r0")
	}
}
