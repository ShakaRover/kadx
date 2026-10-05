package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态与实例 byte 数组字段的初始化都应还原为数组字面量。
 */
class TestArrayInitField : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArrayInitFieldFixture.TestCls::class.java))
			.code()
			.containsOne("static byte[] a = {10, 20, 30};")
			.containsOne("byte[] b = {40, 50, 60};")
	}
}
