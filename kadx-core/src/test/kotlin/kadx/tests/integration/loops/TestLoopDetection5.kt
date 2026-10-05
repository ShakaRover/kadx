package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带 break 的 iterator 循环不应被还原为 for-each。
 */
class TestLoopDetection5 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestLoopDetection5Fixture.TestCls::class.java))
			.code()
			.doesNotContain("for (")
			.containsOne("it.next();")
	}
}
