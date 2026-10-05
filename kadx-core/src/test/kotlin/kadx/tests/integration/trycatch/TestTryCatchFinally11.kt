package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * finally 中对集合的遍历清理逻辑应被正确还原。
 */
class TestTryCatchFinally11 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally11Fixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
	}
}
