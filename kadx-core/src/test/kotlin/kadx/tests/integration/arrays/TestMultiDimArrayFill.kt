package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多维数组填充：外层 `int[][]` 与内层 `int[]` 字面量都应完整还原。
 */
class TestMultiDimArrayFill : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestMultiDimArrayFillFixture.TestCls::class.java))
			.code()
			.contains(
				"return new Obj(" +
					"new int[][]{new int[]{1}, new int[]{2}, new int[]{3}, new int[]{4, 5}, new int[0]}, " +
					"new int[]{a, a, a, a, b});",
			)
	}
}
