package jadx.tests.integration.arith

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 异或 `^` 与布尔取反：`x ^ true` 应还原为 `!x`，`x ^ false` 保持原样。
 *
 * 对应 smali 的等价 Java：
 * ```
 * public boolean test1() {
 * 	return test() ^ true;
 * }
 *
 * public boolean test2() {
 * 	return test() ^ false;
 * }
 *
 * public boolean test() {
 * 	return true;
 * }
 * ```
 */
class TestXor : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestXorFixture.TestCls::class.java))
			.code()
			.containsOne("return !test();")
			.containsOne("return !v;")
	}

	@Test
	fun smali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("return !test();")
			.containsOne("return test();")
	}
}
