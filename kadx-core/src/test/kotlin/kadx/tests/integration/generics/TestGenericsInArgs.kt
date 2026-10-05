package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型方法参数：`List<? super T>` 与 `Set<T>` 的类型变量应保留；无调试信息时参数名退化。
 */
class TestGenericsInArgs : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestGenericsInArgsFixture.TestCls::class.java))
			.code()
			.contains("public static <T> void test(List<? super T> genericList, Set<T> set) {")
			.contains("if (genericList == null) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestGenericsInArgsFixture.TestCls::class.java))
			.code()
			.contains("public static <T> void test(List<? super T> list, Set<T> set) {")
			.contains("if (list == null) {")
	}
}
