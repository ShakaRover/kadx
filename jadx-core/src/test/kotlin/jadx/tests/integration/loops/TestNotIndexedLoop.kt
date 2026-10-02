package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 非索引循环（带提前 break）不应被还原为 for 循环。
 */
class TestNotIndexedLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestNotIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (")
			.containsOne("while (true) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestNotIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (")
			.containsOne("while (true) {")
	}
}
