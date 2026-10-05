package kadx.tests.integration.others

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 拆箱比较：`((Integer) 1).equals(i)` 不应被内联成 `1.equals(i)`。
 */
class TestDeboxing4 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()

		KadxAssertions.assertThat(getClassNode(TestDeboxing4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("return 1.equals(num);")
	}

	@Test
	@NotYetImplemented("Inline boxed types")
	fun testInline() {
		noDebugInfo()

		KadxAssertions.assertThat(getClassNode(TestDeboxing4Fixture.TestCls::class.java))
			.code()
			.containsOne("return ((Integer) 1).equals(i);")
	}
}
