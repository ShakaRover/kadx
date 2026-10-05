package kadx.tests.integration.arith

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 特殊浮点/整型常量：NaN、正负无穷、MIN/MAX/MIN_NORMAL 应还原为具名常量。
 */
class TestSpecialValues : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSpecialValuesFixture.TestCls::class.java))
			.code()
			.containsOne(
				"Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE, Float.MIN_NORMAL",
			)
			.containsOne(
				"Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, " +
					"Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_NORMAL",
			)
			.containsOne("Short.MIN_VALUE, Short.MAX_VALUE")
			.containsOne("Integer.MIN_VALUE, Integer.MAX_VALUE")
			.containsOne("Long.MIN_VALUE, Long.MAX_VALUE")
	}
}
