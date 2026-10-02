package jadx.tests.integration.loops

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
