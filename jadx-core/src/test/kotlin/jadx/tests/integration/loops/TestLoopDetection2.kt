package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * if/else 分支中的变量更新不应被错误地拆分成新的寄存器变量。
 */
class TestLoopDetection2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopDetection2Fixture.TestCls::class.java))
			.code()
			.containsOne("int c = a + b;")
			.containsOne("for (int i = a; i < b; i++) {")
			.doesNotContain("c_2")
	}
}
