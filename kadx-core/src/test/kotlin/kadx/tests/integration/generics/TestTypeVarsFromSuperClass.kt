package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类型变量来自泛型父类：`call()` 的返回类型应推断为 `String`，不产生多余强转。
 */
class TestTypeVarsFromSuperClass : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeVarsFromSuperClassFixture.TestCls::class.java))
			.code()
			.containsOne("= call();")
			.doesNotContain("(String)")
	}
}
