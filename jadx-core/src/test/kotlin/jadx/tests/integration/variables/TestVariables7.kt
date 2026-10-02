package jadx.tests.integration.variables

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
