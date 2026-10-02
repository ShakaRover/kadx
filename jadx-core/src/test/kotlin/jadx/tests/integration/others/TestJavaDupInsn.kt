package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DUP 指令：数组元素与 SSA 变量的赋值顺序应正确还原。
 */
class TestJavaDupInsn : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestJavaDupInsnFixture.TestCls::class.java))
			.code()
	}
}
