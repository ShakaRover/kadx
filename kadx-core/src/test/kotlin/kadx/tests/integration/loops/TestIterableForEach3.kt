package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型 Set 的 for-each 遍历还原。
 */
class TestIterableForEach3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestIterableForEach3Fixture.TestCls::class.java))
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
