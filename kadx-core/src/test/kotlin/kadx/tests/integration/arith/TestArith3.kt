package kadx.tests.integration.arith

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 位运算与 while 循环：`n += len + 5;` 应作为循环体语句，而非被塞进循环条件；
 * `switch` 不应凭空多出 `default:`。
 */
class TestArith3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestArith3Fixture.TestCls::class.java))
			.code()
			.containsOne("while (n + 4 < buffer.length) {")
			.containsOne(indent() + "n += len + 5;")
			.doesNotContain("; n += len + 5) {")
			.doesNotContain("default:")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestArith3Fixture.TestCls::class.java))
			.code()
			.containsOne("while (")
	}
}
