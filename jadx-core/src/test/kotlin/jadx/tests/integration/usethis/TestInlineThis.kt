package jadx.tests.integration.usethis

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 仅用于保存 `this` 的局部变量应被内联消除。
 */
class TestInlineThis : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInlineThisFixture.TestCls::class.java))
			.code()
			.doesNotContain("something")
			.doesNotContain("something.method()")
			.doesNotContain("something.field")
			.doesNotContain("= this")
			.containsOne("this.field = 123;")
			.containsOne("method();")
	}
}
