package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环（含 do-while 与恒真条件）的还原。
 */
class TestEndlessLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestEndlessLoopFixture.TestCls::class.java))
			.code()
			.contains("while (this == this)")
	}
}
