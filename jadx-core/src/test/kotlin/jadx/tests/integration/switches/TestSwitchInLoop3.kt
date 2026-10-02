package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 与 TestSwitchInLoop 类似，但多一个未使用局部变量以强制生成 CFG。
 */
class TestSwitchInLoop3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchInLoop3Fixture.TestCls::class.java))
			.code()
			.containsLines(
				3,
				"switch (k) {",
				indent() + "case 0:",
				indent(2) + "return a;",
				indent() + "default:",
				indent(2) + "a++;",
				indent(2) + "k >>= 1;",
			)
	}
}
