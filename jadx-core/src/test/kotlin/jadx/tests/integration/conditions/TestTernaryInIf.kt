package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * if 中的三元表达式：三元表达式不应被还原成 if/else。
 */
class TestTernaryInIf : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTernaryInIfFixture.TestCls::class.java))
			.code()
			.doesNotContain("if")
			.doesNotContain("else")
			.containsOne("return a ? b : c;")
			.containsOneOf(
				"return (a ? b : c) ? 1 : 2;",
				"return (a ? !b : !c) ? 2 : 1;", // TODO: simplify this
			)
	}
}
