package jadx.tests.integration.loops

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 测试无 else 的 if 语句附近的循环边指令处理：这些边不应被加入 else 区域。
 * 具体条件不重要，关键是反编译能成功。
 */
class TestIfInLoop4 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmaliWithPath("loops", "TestIfInLoop4"))
			.code()
			.containsOne("return true;")
	}
}
