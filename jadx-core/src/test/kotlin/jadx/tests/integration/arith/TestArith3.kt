package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 位运算与 while 循环：`n += len + 5;` 应作为循环体语句，而非被塞进循环条件；
 * `switch` 不应凭空多出 `default:`。
 */
class TestArith3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestArith3Fixture.TestCls::class.java))
			.code()
			.containsOne("while (n + 4 < buffer.length) {")
			.containsOne(indent() + "n += len + 5;")
			.doesNotContain("; n += len + 5) {")
			.doesNotContain("default:")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestArith3Fixture.TestCls::class.java))
			.code()
			.containsOne("while (")
	}
}
