package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 数组初始化与两种 `for` 循环（int 步进、long 递减）应保持原样，不被错误内联。
 */
class TestInline2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInline2Fixture.TestCls::class.java))
			.code()
			.containsOne("int[] a = {1, 2, 4, 6, 8};")
			.containsOne("for (int i = 0; i < a.length; i += 2) {")
			.containsOne("for (long i2 = b; i2 > 0; i2--) {")
	}
}
