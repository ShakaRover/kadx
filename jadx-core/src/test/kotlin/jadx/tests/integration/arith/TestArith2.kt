package jadx.tests.integration.arith

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 算术表达式括号：结合性与运算符优先级应被正确还原，不多写也不漏写括号。
 */
class TestArith2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestArith2Fixture.TestCls::class.java))
			.code()
			.contains("return (a + 2) * 3;")
			.doesNotContain("a + 2 * 3")
			.contains("return a + b + c;")
			.doesNotContain("return (a + b) + c;")
			.contains("return a | b | c;")
			.doesNotContain("return (a | b) | c;")
			.contains("return a & b & c;")
			.doesNotContain("return (a & b) & c;")
			.contains("return a - (b - c);")
			.doesNotContain("return a - b - c;")
			.contains("return a / (b / c);")
			.doesNotContain("return a / b / c;")
	}
}
