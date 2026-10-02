package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带提前 break 的索引循环不应被还原为 for-each。
 */
class TestIndexedLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (File file :")
			.containsOne("for (int i = 0; i < length; i++) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestIndexedLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (File file :")
			.containsOne("for (int i = 0; i < length; i++) {")
	}
}
