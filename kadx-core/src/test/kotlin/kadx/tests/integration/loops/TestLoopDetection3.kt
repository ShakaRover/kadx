package kadx.tests.integration.loops

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带前置自减条件的 while 循环（`--pos >= 0`）。
 */
class TestLoopDetection3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopDetection3Fixture.TestCls::class.java))
			.code()
			.contains("while")
	}

	@Test
	@NotYetImplemented
	fun test2() {
		KadxAssertions.assertThat(getClassNode(TestLoopDetection3Fixture.TestCls::class.java))
			.code()
			.contains("while (--pos >= 0) {")
	}
}
