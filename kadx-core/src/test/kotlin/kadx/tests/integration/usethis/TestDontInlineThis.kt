package kadx.tests.integration.usethis

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * `this` 赋值给局部变量后又在分支中返回时，不能把该局部变量内联掉。
 */
class TestDontInlineThis : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestDontInlineThisFixture.TestCls::class.java))
			.code()
			.containsOne("TestDontInlineThisFixture\$TestCls res")
			.containsOne("res = this;")
			.containsOne("res = new TestDontInlineThisFixture\$TestCls();")
	}
}
