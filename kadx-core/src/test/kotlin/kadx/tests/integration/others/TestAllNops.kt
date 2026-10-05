package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证 NOP 指令被正确忽略：只含 NOP（及 try/catch 包裹的 NOP）的方法应反编译为空方法体。
 */
class TestAllNops : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsLines(1, "private boolean test() {", "}")
			.containsLines(1, "private boolean testWithTryCatch() {", "}")
	}
}
