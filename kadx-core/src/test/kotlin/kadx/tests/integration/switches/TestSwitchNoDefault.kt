package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 无 default 的 switch：所有 case 均以 break 结尾。
 */
class TestSwitchNoDefault : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchNoDefaultFixture.TestCls::class.java))
			.code()
			.countString(4, "break;")
			.containsOne("System.out.println(s);")
	}
}
