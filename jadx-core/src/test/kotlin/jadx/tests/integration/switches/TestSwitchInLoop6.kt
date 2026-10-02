package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * while(true) 内 switch 含提前 return 与 break：应还原为 do-while 形式。
 */
class TestSwitchInLoop6 : IntegrationTest() {

	@Test
	fun test() {
		allowWarnInCode()
		assertThat(getClassNode(TestSwitchInLoop6Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (n) {")
			.containsOne("case 1:")
			.containsOne("case 2:")
			.containsOne("case 3:")
			.containsOne("case 4:")
			.containsOne("do {")
			.containsOne("while (getN() != 3)")
	}
}
