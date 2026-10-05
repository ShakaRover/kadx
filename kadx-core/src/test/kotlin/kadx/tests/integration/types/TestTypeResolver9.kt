package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `byte` 参与乘法与 `& 0xFF` 运算：不应把常量错误还原成 `Byte.MIN_VALUE`。
 */
class TestTypeResolver9 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver9Fixture.TestCls::class.java))
			.code()
			.containsOne("return 16777216 * b;")
			.doesNotContain("Byte.MIN_VALUE")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestTypeResolver9Fixture.TestCls::class.java)
	}
}
