package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 14：三元表达式不应被误判为可简化的布尔表达式，应展开为 if/else 赋值。
 */
class TestConditions14 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConditions14Fixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"boolean r;",
				"if (a == null) {",
				indent() + "r = b != null;",
				"} else {",
				indent() + "r = !a.equals(b);",
				"}",
			)
			.containsOne("if (r) {")
			.containsOne("System.out.println(\"r=\" + r);")
	}
}
