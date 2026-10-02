package jadx.tests.integration.arrays

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 反例：元素之间存在依赖关系（`arr[1] = arr[0] + 1`）时不应合并为数组字面量。
 */
class TestArrayFillNegative : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArrayFillNegativeFixture.TestCls::class.java))
			.code()
			.doesNotContain("int[] arr = {1, ")
			.containsOne("int[] arr = new int[3];")
			.containsOne("arr[1] = arr[0] + 1;")
	}
}
