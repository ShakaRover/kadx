package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 字符串数组填充应还原为数组字面量。
 */
class TestArrayFill : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestArrayFillFixture.TestCls::class.java))
			.code()
			.contains("return new String[]{\"1\", \"2\", \"3\"};")
	}
}
