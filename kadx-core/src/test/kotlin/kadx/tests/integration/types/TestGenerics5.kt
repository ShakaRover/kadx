package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
