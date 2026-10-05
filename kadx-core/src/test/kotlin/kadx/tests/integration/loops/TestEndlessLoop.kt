package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环（含 do-while 与恒真条件）的还原。
 */
class TestEndlessLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestEndlessLoopFixture.TestCls::class.java))
			.code()
			.contains("while (this == this)")
	}
}
