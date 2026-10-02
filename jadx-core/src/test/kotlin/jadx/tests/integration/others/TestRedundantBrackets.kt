package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 冗余括号：反编译输出不应出现无意义的括号（如 `(-1)`、空的 `return;`），
 * 同时保留有语义的强制转换括号。
 */
class TestRedundantBrackets : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestRedundantBracketsFixture.TestCls::class.java))
			.code()
			.doesNotContain("(-1)")
			.doesNotContain("return;")
			.contains("if (obj instanceof String) {")
			.contains("return ((String) obj).length();")
			.contains("a + b < 10")
			.contains("(a & b) != 0")
			.contains("if (num == 4 || num == 6 || num == 8 || num == 10)")
			.contains("a[1] = n * 2;")
			.contains("a[n - 1] = 1;")
			.contains("public int method2(Object obj) {")
			// 参数类型不会被改为 String
			// 强制转换也不会被消除
			.contains("((String) obj).length()")
	}
}
