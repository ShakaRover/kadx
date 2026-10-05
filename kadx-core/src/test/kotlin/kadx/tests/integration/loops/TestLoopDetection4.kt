package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * iterator.hasNext() 驱动的 while 循环。
 */
class TestLoopDetection4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopDetection4Fixture.TestCls::class.java))
			.code()
			.containsOne("while (this.iterator.hasNext()) {")
			.containsOne("if (filtered != null) {")
			.containsOne("return filtered;")
			.containsOne("return null;")
	}
}
