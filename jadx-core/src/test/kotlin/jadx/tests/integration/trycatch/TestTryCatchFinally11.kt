package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
