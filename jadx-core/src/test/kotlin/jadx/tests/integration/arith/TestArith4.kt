package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 位运算还原：`& 255`、无符号右移 `>>>` 与按位或/与的组合表达式。
 */
class TestArith4 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArith4Fixture.TestCls::class.java))
			.code()
			.containsOne("int k = b & 7;")
			.containsOne("& 255")
			.containsOneOf("return (1 - k) & (1 + k);", "return (1 - k) & (k + 1);")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestArith4Fixture.TestCls::class.java))
			.code()
			.containsOne("& 255")
	}
}
