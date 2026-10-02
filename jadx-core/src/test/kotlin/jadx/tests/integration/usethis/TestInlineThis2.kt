package jadx.tests.integration.usethis

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
