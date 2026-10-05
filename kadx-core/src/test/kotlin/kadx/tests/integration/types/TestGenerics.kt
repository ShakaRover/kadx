package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型方法返回自身类型：`TestCls<T> data(T t)` 的泛型签名应完整还原。
 */
class TestGenerics : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestGenericsFixture.TestCls::class.java))
			.code()
			.containsOne("TestCls<T> data(T t) {")
	}

	@Test
	fun test2() {
		noDebugInfo()
		assertThat(getClassNode(TestGenericsFixture.TestCls::class.java))
			.code()
			.containsOne("TestCls<T> data(T t) {")
	}
}
