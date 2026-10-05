package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 数组类型推断：`toChars(bytes)` 的结果变量应推断为 `char[]`（无调试信息时也成立）。
 */
class TestArrays4 : IntegrationTest() {

	@Test
	fun testArrayTypeInference() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestArrays4Fixture.TestCls::class.java))
			.code()
			.containsOne("char[] chars = toChars(bArr);")
	}
}
