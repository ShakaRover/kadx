package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 三元表达式 3：`n == null || !(arg instanceof Named)` 的合并条件应被保留。
 */
class TestTernary3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTernary3Fixture.TestCls::class.java))
			.code()
			.containsOne("if (n == null || !(arg instanceof Named)) {")
			.containsOne("return n.equals(((Named) arg).getName());")
			.doesNotContain("if ((arg instanceof RegisterArg)) {")
	}
}
