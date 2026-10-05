package kadx.tests.integration.synchronize

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环内的 synchronized 块与 break：同步块之后的自增和调用应保持在循环体内。
 */
class TestSynchronized3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSynchronized3Fixture.TestCls::class.java))
			.code()
			.containsLines(3, "}", "this.x++;", "f();")
	}
}
