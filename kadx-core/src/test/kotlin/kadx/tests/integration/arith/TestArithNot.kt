package kadx.tests.integration.arith

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 测试：按位取反应还原为 `~`，而不是 `^`。
 *
 * 对应 smali 的等价 Java：
 * ```
 * public static class TestCls {
 * 	public int test1(int a) {
 * 		return ~a;
 * 	}
 *
 * 	public long test2(long b) {
 * 		return ~b;
 * 	}
 * }
 * ```
 */
class TestArithNot : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPath("arith", "TestArithNot"))
			.code()
			.contains("return ~a;")
			.contains("return ~b;")
			.doesNotContain("^")
	}
}
