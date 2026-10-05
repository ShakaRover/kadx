package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带提前 break 的索引循环不应被还原为 for-each。
 */
class TestIndexedLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (File file :")
			.containsOne("for (int i = 0; i < length; i++) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (File file :")
			.containsOne("for (int i = 0; i < length; i++) {")
	}
}
