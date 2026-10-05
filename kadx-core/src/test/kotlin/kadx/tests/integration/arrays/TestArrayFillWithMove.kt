package kadx.tests.integration.arrays

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：带 move 的 `fill-array-data` 仍应还原为数组字面量。
 */
class TestArrayFillWithMove : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("TestCls"))
			.code()
			.doesNotContain("// fill-array-data instruction")
			.doesNotContain("arr[0] = 0;")
			.contains("return new long[]{0, 1}")
	}
}
