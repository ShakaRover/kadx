package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型 Set 的 for-each 遍历还原。
 */
class TestIterableForEach3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestIterableForEach3Fixture.TestCls::class.java))
			.code()
			.containsOne("for (T s : set) {")
			.containsOne("if (str.length() == 0) {")
		// TODO move return outside 'if'
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestIterableForEach3Fixture.TestCls::class.java)
	}
}
