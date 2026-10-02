package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * `if (tile == null || tile.y != 100)` 这种复合条件中的 break 应被正确还原。
 */
class TestBreakInComplexIf : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestBreakInComplexIfFixture.TestCls::class.java))
			.code()
			.containsOne("if (tile == null || tile.y != 100) {")
			.containsOne("break;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestBreakInComplexIfFixture.TestCls::class.java))
			.code()
			.containsOne("break;")
	}
}
