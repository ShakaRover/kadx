package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内联赋值：`str.isEmpty() || (len = str.length()) > 5` 中的赋值应保持内联。
 */
class TestInnerAssign : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()

		assertThat(getClassNode(TestInnerAssignFixture.TestCls::class.java))
			.code()
			.containsOne("str.length()")
			.containsOne("System.out.println(\"done\");")
	}
}
