package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `InheritableThreadLocal<Map<String, String>>` 的泛型还原：两处局部变量声明都应带上泛型。
 */
class TestGenerics5 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics5Fixture.TestCls::class.java))
			.code()
			.countString(2, "Map<String, String> map = this.inheritableThreadLocal.get();")
	}
}
