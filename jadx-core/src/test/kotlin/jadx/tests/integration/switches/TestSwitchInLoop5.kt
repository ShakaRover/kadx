package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
