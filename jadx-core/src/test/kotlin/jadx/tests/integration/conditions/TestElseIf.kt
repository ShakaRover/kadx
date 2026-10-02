package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * else-if：多分支赋值的 if/else 链应保持原结构，且不生成三元运算符。
 */
class TestElseIf : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestElseIfFixture.TestCls::class.java))
			.code()
			.containsOne("} else if (str.equals(\"b\")) {")
			.containsOne("} else {")
			.containsOne("int r;")
			.containsOne("r = 1;")
			.containsOne("r = -1;")
			.doesNotContain(" ? ")
			.doesNotContain(" : ") // no ternary operator
	}
}
