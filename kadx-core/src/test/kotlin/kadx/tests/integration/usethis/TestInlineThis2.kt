package kadx.tests.integration.usethis

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 指向 `this` 的局部变量若参与空判断等操作，应被内联回 `this`。
 */
class TestInlineThis2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInlineThis2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("thisVar")
			.doesNotContain("thisVar.method()")
			.doesNotContain("thisVar.field")
			.doesNotContain("= this")
			.containsOne("if (Objects.isNull(this)) {")
			.containsOne("this.field = 123;")
			.containsOne("method();")
	}
}
