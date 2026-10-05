package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * while(true) 内 switch 调用方法：两个 case 都 return。
 */
class TestSwitchInLoop2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchInLoop2Fixture.TestCls::class.java))
			.code()
			.containsOne("while (true) {")
			.containsOne("switch (call()) {")
	}
}
