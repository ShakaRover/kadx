package kadx.tests.integration.variables

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 测试：循环中使用的变量应还原为 `iMth`，不残留 `i2`。
 */
class TestVariablesInLoop : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("int iMth;")
			.countString(2, "iMth = 0;")
			.doesNotContain("i2")
	}
}
