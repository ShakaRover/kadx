package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器中的泛型局部变量：有调试信息时保留 `Map<String, String>`，无调试信息时退化为强转。
 */
class TestConstructorGenerics : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConstructorGenericsFixture.TestCls::class.java))
			.code()
			.containsOne("Map<String, String> map = new HashMap<>();")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestConstructorGenericsFixture.TestCls::class.java))
			.code()
			.containsOne("return (String) new HashMap().get(\"test\");")
	}
}
