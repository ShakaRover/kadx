package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
