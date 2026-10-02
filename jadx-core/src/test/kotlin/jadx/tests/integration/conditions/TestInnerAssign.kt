package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
