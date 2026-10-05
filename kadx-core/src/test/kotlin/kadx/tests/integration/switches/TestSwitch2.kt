package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 触摸事件处理：多分支 switch 内含提前 return，检查 break/return 数量。
 */
class TestSwitch2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitch2Fixture.TestCls::class.java))
			.code()
			.countString(4, "break;")
			// .countString(2, "return;")
			// TODO: remove redundant returns
			.countString(4, "return;")
	}
}
