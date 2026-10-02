package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
