package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型方法重载：`declare(String.class)` 应匹配到泛型版本而不是 `Object` 版本。
 */
class TestGenerics7 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestGenerics7Fixture.TestCls::class.java))
			.code()
			.contains("declare(String.class);")
	}
}
