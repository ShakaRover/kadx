package jadx.tests.integration.synchronize

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
