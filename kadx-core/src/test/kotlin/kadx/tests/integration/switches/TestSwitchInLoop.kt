package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * while(true) 内 switch，case 0 直接 return。
 */
class TestSwitchInLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSwitchInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("switch (k) {")
			.containsOne("case 0:")
			.containsOne("return a;")
			.containsOne("default:")
			.containsOne("a++;")
			.containsOne("k >>= 1;")
	}
}
