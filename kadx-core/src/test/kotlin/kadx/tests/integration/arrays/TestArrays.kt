package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 数组字面量直接下标访问：应还原为 `new int[]{...}[i]`。
 */
class TestArrays : IntegrationTest() {
	@Test
	fun test() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestArraysFixture.TestCls::class.java))
			.code()
			.containsOne("return new int[]{1, 2, 3, 5}[i];")
	}
}
