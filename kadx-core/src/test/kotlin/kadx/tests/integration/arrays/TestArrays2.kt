package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多种原始类型数组分支：int 数组字面量应被正确还原。
 */
class TestArrays2 : IntegrationTest() {
	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestArrays2Fixture.TestCls::class.java))
			.code()
			.containsOne("new int[]{1, 2}")
	}
}
