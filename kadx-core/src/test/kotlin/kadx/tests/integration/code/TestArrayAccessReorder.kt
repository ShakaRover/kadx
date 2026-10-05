package kadx.tests.integration.code

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 数组访问重排：循环内先读数组、再自增下标的访问顺序应保留为 `i++`。
 */
class TestArrayAccessReorder : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestArrayAccessReorderFixture.TestCls::class.java))
			.code()
			.containsOne("i++")
	}
}
