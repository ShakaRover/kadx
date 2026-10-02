package jadx.tests.integration.others

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 拆箱比较：`((Integer) 1).equals(i)` 不应被内联成 `1.equals(i)`。
 */
class TestDeboxing4 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()

		JadxAssertions.assertThat(getClassNode(TestDeboxing4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("return 1.equals(num);")
	}

	@Test
	@NotYetImplemented("Inline boxed types")
	fun testInline() {
		noDebugInfo()

		JadxAssertions.assertThat(getClassNode(TestDeboxing4Fixture.TestCls::class.java))
			.code()
			.containsOne("return ((Integer) 1).equals(i);")
	}
}
