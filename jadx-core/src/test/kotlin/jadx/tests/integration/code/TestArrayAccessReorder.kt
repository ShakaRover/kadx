package jadx.tests.integration.code

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
