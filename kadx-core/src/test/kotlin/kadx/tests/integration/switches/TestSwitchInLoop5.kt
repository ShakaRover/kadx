package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * while(true) 内 switch：default 分支打印并返回。
 */
class TestSwitchInLoop5 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchInLoop5Fixture.TestCls::class.java))
			.code()
			.containsOne("default:")
			.containsOne("System.out.println(")
	}
}
