package jadx.tests.integration.loops

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带前置自减条件的 while 循环（`--pos >= 0`）。
 */
class TestLoopDetection3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopDetection3Fixture.TestCls::class.java))
			.code()
			.contains("while")
	}

	@Test
	@NotYetImplemented
	fun test2() {
		JadxAssertions.assertThat(getClassNode(TestLoopDetection3Fixture.TestCls::class.java))
			.code()
			.contains("while (--pos >= 0) {")
	}
}
