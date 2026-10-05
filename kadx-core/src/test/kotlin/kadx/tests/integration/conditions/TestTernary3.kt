package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 三元表达式 3：`n == null || !(arg instanceof Named)` 的合并条件应被保留。
 */
class TestTernary3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTernary3Fixture.TestCls::class.java))
			.code()
			.containsOne("if (n == null || !(arg instanceof Named)) {")
			.containsOne("return n.equals(((Named) arg).getName());")
			.doesNotContain("if ((arg instanceof RegisterArg)) {")
	}
}
