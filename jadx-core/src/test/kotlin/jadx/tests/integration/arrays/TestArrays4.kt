package jadx.tests.integration.arrays

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 数组类型推断：`toChars(bytes)` 的结果变量应推断为 `char[]`（无调试信息时也成立）。
 */
class TestArrays4 : IntegrationTest() {

	@Test
	fun testArrayTypeInference() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestArrays4Fixture.TestCls::class.java))
			.code()
			.containsOne("char[] chars = toChars(bArr);")
	}
}
