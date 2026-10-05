package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * switch 外层再包 if，case 1 条件 break 否则贯穿到 case 2。
 */
class TestSwitchWithFallThroughCase2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSwitchWithFallThroughCase2Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (a % 4) {")
			.containsOne("if (a == 5 && b) {")
			.containsOne("if (b) {")
	}
}
