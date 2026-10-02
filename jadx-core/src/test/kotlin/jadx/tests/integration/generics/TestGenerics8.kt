package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 继承泛型集合（`LinkedHashMap`）并实现 `Iterable`：`keySet().iterator()` 应直接返回。
 */
class TestGenerics8 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics8Fixture.TestCls::class.java))
			.code()
			.containsOne("return keySet().iterator();")
	}
}
