package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 检查 switch 之后的公共代码只被还原一次。
 */
class TestSwitchInLoop9 : IntegrationTest() {

	@Test
	fun test() {
		// Checks that the work after the switch is recovered only once
		assertThat(getClassNode(TestSwitchInLoop9Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (n) {")
			.containsOne("case 0:")
			.containsOne("case 1:")
			.containsOne("while (")
			.containsOne("default")
			.containsOne("i += 327;")
	}
}
