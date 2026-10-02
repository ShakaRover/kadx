package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * long 位移强转：`(long) c << 32` 的强转位置应保留（允许两种等价括号形式）。
 */
class TestLongCast : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestLongCastFixture.TestCls::class.java))
			.code()
			.containsOneOf(
				"return (long) c << 32;",
				"return ((long) c) << 32;",
			)
	}
}
