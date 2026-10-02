package jadx.tests.integration.conditions

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
