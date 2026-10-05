package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 带标签 break 跳出 while：反编译为 `return` 语句。
 */
class TestSwitchBreak : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchBreakFixture.TestCls::class.java))
			.code()
			.contains("switch (a % 4) {")
			.countString(4, "case ")
			.countString(2, "break;")
			.doesNotContain("default:")
			// TODO finish break with label from switch
			.containsOne("return s + \"+\";")
	}
}
