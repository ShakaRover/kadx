package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
