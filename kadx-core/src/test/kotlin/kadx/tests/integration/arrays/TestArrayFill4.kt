package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 常量被替换不应破坏已填充数组的生成（不应出现 `new long[ARRAY_SIZE];`）。
 */
class TestArrayFill4 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestArrayFill4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("new long[ARRAY_SIZE];")
			.containsOne("return new long[]{0, 1, ")
	}
}
