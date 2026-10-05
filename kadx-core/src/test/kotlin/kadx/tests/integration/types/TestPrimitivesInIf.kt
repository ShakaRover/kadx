package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * if 条件中的基本类型比较：`short` 与 `int` 的比较应保留各自原始类型，不应统一为 int。
 */
class TestPrimitivesInIf : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestPrimitivesInIfFixture.TestCls::class.java))
			.code()
			.containsOne("short sh = Short.parseShort(str);")
			.containsOne("int i = Integer.parseInt(str);")
			.containsOne("return sh == i;")
	}

	@Test
	fun test2() {
		noDebugInfo()
		assertThat(getClassNode(TestPrimitivesInIfFixture.TestCls::class.java))
			.code()
			.containsOne("short s = Short.parseShort(str);")
	}
}
