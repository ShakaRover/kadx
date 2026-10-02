package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 可变参数 `Object...` 的元素强转：应保留 `(Integer)` / `(String)` 显式强转。
 */
class TestTypeResolver11 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver11Fixture.TestCls::class.java))
			.code()
			.containsOne("(Integer) objects[0]")
			.containsOne("String str = (String) objects[1];")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver11Fixture.TestCls::class.java))
			.code()
			.containsOne("(Integer) objArr[0]")
			.containsOne("String str = (String) objArr[1];")
	}
}
