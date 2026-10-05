package kadx.tests.integration.others

import kadx.tests.api.RaungTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DUP2_X2 指令：double 数组元素赋值应正确还原。
 */
class TestJavaDup2x2 : RaungTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromRaung())
			.code()
			.containsOne("dArr[0] = 127.5d;")
	}
}
