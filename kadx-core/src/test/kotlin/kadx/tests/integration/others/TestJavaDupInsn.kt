package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
