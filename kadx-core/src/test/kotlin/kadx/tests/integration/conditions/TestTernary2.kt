package kadx.tests.integration.conditions

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 三元表达式 2：`checkFalse(f(1, 0) == 0)` 的调用应被保留。
 */
class TestTernary2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTernary2Fixture.TestCls::class.java))
			.code()
			.containsOne("f(1, 0)")
	}

	@Test
	@NotYetImplemented
	fun test2() {
		assertThat(getClassNode(TestTernary2Fixture.TestCls::class.java))
			.code()
			.contains("assertTrue(f(1, 0) == 0);")
	}
}
