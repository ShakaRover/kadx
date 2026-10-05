package kadx.tests.integration.loops

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 测试当 break 边指令已存在时不会错误插入 continue，导致 continue 丢失并反编译失败。
 */
class TestBreakInLoop6 : SmaliTest() {
	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali()).code().containsOne("break")
	}
}
