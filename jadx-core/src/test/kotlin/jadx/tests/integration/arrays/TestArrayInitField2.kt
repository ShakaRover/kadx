package jadx.tests.integration.arrays

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：静态数组字段的初始化应合并为数组字面量。
 */
class TestArrayInitField2 : SmaliTest() {
	@Test
	fun test() {
		forceDecompiledCheck()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("static long[] myArr = {1282979400, 0, 0};")
	}
}
