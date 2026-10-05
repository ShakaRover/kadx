package kadx.tests.integration.debuginfo

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 变量名生成：参数寄存器被不同类型的变量复用，且调试信息中没有变量名。
 *
 * 对应的 Java 形态（仅作说明，实际使用 smali 输入）：
 * ```
 * public void test(String s, int k) {
 * 	f1(s);
 * 	int i = k + 3;
 * 	String s2 = "i" + i;
 * 	f2(i, s2);
 * 	double d = i * 5;
 * 	String s3 = "d" + d;
 * 	f3(d, s3);
 * }
 * ```
 */
class TestVariablesNames : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPath("debuginfo", "TestVariablesNames"))
			.code()
			// TODO: don't use current variables naming in tests
			.containsOne("f1(str);")
			.containsOne("f2(i2, \"i\" + i2);")
			.containsOne("f3(d, \"d\" + d);")
	}
}
