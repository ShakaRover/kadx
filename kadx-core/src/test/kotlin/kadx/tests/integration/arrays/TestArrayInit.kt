package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 局部数组变量与实例字段赋值的数组初始化：前者可内联为字面量，后者保留 `new byte[]{...}`。
 */
class TestArrayInit : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestArrayInitFixture.TestCls::class.java))
			.code()
			.contains("= {10, 20, 30};")
			.contains("this.bytes = new byte[]{10, 20, 30};")
	}
}
