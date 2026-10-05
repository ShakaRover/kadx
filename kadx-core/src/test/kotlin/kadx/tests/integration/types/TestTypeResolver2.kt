package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 空值判断与异常抛出：`obj != null` 的判断应被正确还原。
 */
class TestTypeResolver2 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver2Fixture.TestCls::class.java))
			.code()
			.containsOne("if (obj != null) {")
	}
}
