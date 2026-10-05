package kadx.tests.integration.arrays

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：`fill-array-data` 无法合并时逐元素赋值应保留。
 */
class TestFillArrayData : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("TestCls"))
			.code()
			.contains("jArr[0] = 1;")
			.contains("jArr[1] = 2;")
	}
}
