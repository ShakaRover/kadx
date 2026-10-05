package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * `if (tile == null || tile.y != 100)` 这种复合条件中的 break 应被正确还原。
 */
class TestBreakInComplexIf : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestBreakInComplexIfFixture.TestCls::class.java))
			.code()
			.containsOne("if (tile == null || tile.y != 100) {")
			.containsOne("break;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestBreakInComplexIfFixture.TestCls::class.java))
			.code()
			.containsOne("break;")
	}
}
