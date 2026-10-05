package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
