package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * switch 分支中 `continue` 跳过本轮循环：应保留 `continue;`。
 */
class TestSwitchContinue : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchContinueFixture.TestCls::class.java))
			.code()
			.contains("switch (a % 4) {")
			.countString(4, "case ")
			.countString(2, "break;")
			.containsOne("a -= 2;")
			.containsOne("continue;")
	}
}
