package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `byte` 数组元素与 0 比较：`||` 组合条件应保留。
 */
class TestTypeResolver4 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver4Fixture.TestCls::class.java))
			.code()
			.containsOne("(strArray[end] != 0 || strArray[end + 1] != 0)")
	}

	@Test
	fun test2() {
		noDebugInfo()
		getClassNode(TestTypeResolver4Fixture.TestCls::class.java)
	}
}
