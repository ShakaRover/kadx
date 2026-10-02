package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套 if：`if / else if` 与 `||` 条件组合应完整保留。
 */
class TestNestedIf : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestNestedIfFixture.TestCls::class.java))
			.code()
			.containsOne("if (this.a0) {")
			.containsOne("if (this.a1 == 0 || this.a2 == 0) {")
			.containsOne("} else if (this.a3 == 0 || this.a4 == 0) {")
			.countString(2, "return false;")
			.containsOne("test1();")
			.containsOne("return true;")
	}
}
