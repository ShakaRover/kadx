package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * while(true) 内 switch，case 0 直接 return。
 */
class TestSwitchInLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSwitchInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("switch (k) {")
			.containsOne("case 0:")
			.containsOne("return a;")
			.containsOne("default:")
			.containsOne("a++;")
			.containsOne("k >>= 1;")
	}
}
