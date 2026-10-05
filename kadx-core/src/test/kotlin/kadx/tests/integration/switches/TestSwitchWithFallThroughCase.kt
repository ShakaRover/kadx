package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * case 1 条件 break，否则贯穿到 case 2。
 */
class TestSwitchWithFallThroughCase : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSwitchWithFallThroughCaseFixture.TestCls::class.java))
			.code()
			.containsOne("switch (a % 4) {")
			.containsOne("if (a == 5 && b) {")
			.containsOne("if (b) {")
	}
}
