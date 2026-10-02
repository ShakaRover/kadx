package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * return 包装：switch/三元表达式/循环中的返回值应原样输出，
 * 常量折叠（如 `arg0 -= 951;`）应正确完成。
 */
class TestReturnWrapping : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestReturnWrappingFixture.TestCls::class.java))
			.code()
			.contains("return 255;")
			.contains("return arg0 + 1;").contains("return i > 128 ? arg0.toString() + ret.toString() : Integer.valueOf(i);")
			.contains("return arg0 + 2;")
			.contains("arg0 -= 951;")
	}
}
