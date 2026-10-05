package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 数组类型推断：`new Object[] { e }` 的参数类型应正确还原（调试/非调试信息下变量名不同）。
 */
class TestArrayTypes : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArrayTypesFixture.TestCls::class.java))
			.code()
			.containsOne("use(new Object[]{e});")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestArrayTypesFixture.TestCls::class.java))
			.code()
			.containsOne("use(new Object[]{exc});")
	}
}
