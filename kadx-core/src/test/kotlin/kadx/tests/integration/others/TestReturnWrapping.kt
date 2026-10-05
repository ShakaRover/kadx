package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * return 包装：switch/三元表达式/循环中的返回值应原样输出，
 * 常量折叠（如 `arg0 -= 951;`）应正确完成。
 */
class TestReturnWrapping : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestReturnWrappingFixture.TestCls::class.java))
			.code()
			.contains("return 255;")
			.contains("return arg0 + 1;").contains("return i > 128 ? arg0.toString() + ret.toString() : Integer.valueOf(i);")
			.contains("return arg0 + 2;")
			.contains("arg0 -= 951;")
	}
}
