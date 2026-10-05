package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * case 贯穿：case 1 落空到 case 2 共用逻辑，反编译应保留赋值与 break。
 */
class TestSwitchFallThrough : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchFallThroughFixture.TestCls::class.java))
			.code()
			.containsOne("switch (a) {")
			.containsOne("r = i;")
			.containsOne("r = -1;")
			.countString(2, "break;")
		// code correctness checks done in 'check' method
	}
}
