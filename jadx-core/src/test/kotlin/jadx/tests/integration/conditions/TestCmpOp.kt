package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 比较运算符：float/long/double 的各种比较与常量顺序都应原样保留。
 */
class TestCmpOp : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestCmpOpFixture.TestCls::class.java))
			.code()
			.contains("return a > 3.0f;")
			.contains("return b < 2.0f;")
			.contains("return c == 1.0f;")
			.contains("return d != 0.0f;")
			.contains("return e >= -1.0f;")
			.contains("return f <= -2.0f;")
			.contains("return 4.0f > g;")
			.contains("return 5 < h;").contains("return 6.5d < i;")
	}
}
