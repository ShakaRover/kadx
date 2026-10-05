package kadx.tests.integration.arith

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 无符号比较技巧：`x + Integer.MIN_VALUE` 应出现两次，不被优化掉。
 */
class TestSpecialValues2 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestSpecialValues2Fixture.TestCls::class.java))
			.code()
			.countString(2, "Integer.MIN_VALUE")
	}
}
