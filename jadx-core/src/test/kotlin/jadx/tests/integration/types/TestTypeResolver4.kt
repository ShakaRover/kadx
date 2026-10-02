package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
