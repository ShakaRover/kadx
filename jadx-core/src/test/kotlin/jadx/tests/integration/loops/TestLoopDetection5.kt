package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带 break 的 iterator 循环不应被还原为 for-each。
 */
class TestLoopDetection5 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestLoopDetection5Fixture.TestCls::class.java))
			.code()
			.doesNotContain("for (")
			.containsOne("it.next();")
	}
}
