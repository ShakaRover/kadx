package jadx.tests.integration.arrays

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 字符串数组填充应还原为数组字面量。
 */
class TestArrayFill : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestArrayFillFixture.TestCls::class.java))
			.code()
			.contains("return new String[]{\"1\", \"2\", \"3\"};")
	}
}
