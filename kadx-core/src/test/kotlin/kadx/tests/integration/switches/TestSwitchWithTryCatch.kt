package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * switch 分支内嵌 try/catch，return 与 break 混合：检查还原计数。
 */
@SuppressWarnings("checkstyle:printstacktrace")
class TestSwitchWithTryCatch : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchWithTryCatchFixture.TestCls::class.java))
			.code()
			.countString(3, "break;")
			.countString(4, "return;")
	}
}
