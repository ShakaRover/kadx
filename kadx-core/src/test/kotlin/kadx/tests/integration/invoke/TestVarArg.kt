package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 变参方法声明与调用：正例（变参）与反例（数组参数）都应正确区分。
 */
class TestVarArg : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestVarArgFixture.TestCls::class.java))
			.code()
			.contains("void test1(int... a) {")
			.contains("void test2(int i, Object... a) {")
			.contains("test1(1, 2);")
			.contains("test2(3, \"1\", 7);")
			// negative case
			.contains("void test3(int[] a) {")
			.contains("test3(new int[]{5, 8});")
	}
}
