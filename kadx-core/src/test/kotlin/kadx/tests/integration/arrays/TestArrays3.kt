package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 对象数组包装数组参数：`new Object[]{bArr}` 应保留（含无调试信息场景）。
 */
class TestArrays3 : IntegrationTest() {
	@Test
	fun test() {
		assertThat(getClassNode(TestArrays3Fixture.TestCls::class.java))
			.code()
			.containsOne("return new Object[]{bArr};")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestArrays3Fixture.TestCls::class.java))
			.code()
			.containsOne("return new Object[]{bArr};")
	}
}
