package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 条件 10：或运算与范围判断组合，else 分支应保留且不产生多余 return。
 */
class TestConditions10 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConditions10Fixture.TestCls::class.java))
			.code()
			.doesNotContain("return")
			.containsOne("if (a || b > 2) {")
			.containsOne("b++;")
			.containsOne("if (!a || (b >= 0 && b <= 11)) {")
			.containsOne("System.out.println(\"1\");")
			.containsOne("} else {")
			.containsOne("System.out.println(\"2\");")
			.containsOne("System.out.println(\"3\");")
	}
}
