package jadx.tests.integration.usethis

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * `this` 赋值给局部变量后又在分支中返回时，不能把该局部变量内联掉。
 */
class TestDontInlineThis : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestDontInlineThisFixture.TestCls::class.java))
			.code()
			.containsOne("TestDontInlineThisFixture\$TestCls res")
			.containsOne("res = this;")
			.containsOne("res = new TestDontInlineThisFixture\$TestCls();")
	}
}
