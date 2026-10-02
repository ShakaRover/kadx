package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
