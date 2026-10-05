package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue #977：数组 for-each 中带 return 的循环不应被还原为 while。
 */
class TestArrayForEach3 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestArrayForEach3Fixture.TestCls::class.java))
			.code()
			.doesNotContain("while")
			.containsOne("for (String str : strArr) {")
	}
}
