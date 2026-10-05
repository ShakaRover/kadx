package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 复杂 if 条件中的 break（含 continue 与多个 break 分支）应被正确还原。
 */
class TestBreakInComplexIf2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestBreakInComplexIf2Fixture.TestCls::class.java))
			.code()
			.countString(2, "break;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestBreakInComplexIf2Fixture.TestCls::class.java))
			.code()
			.countString(2, "break;")
	}
}
