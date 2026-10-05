package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 非索引循环（带提前 break）不应被还原为 for 循环。
 */
class TestNotIndexedLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestNotIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (")
			.containsOne("while (true) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestNotIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (")
			.containsOne("while (true) {")
	}
}
