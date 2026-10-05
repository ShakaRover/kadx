package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 检查 default continue 分支不会被错误删除。
 */
class TestSwitchInLoop8 : IntegrationTest() {

	@Test
	fun test() {
		// Checks that the default continue case is not removed
		assertThat(getClassNode(TestSwitchInLoop8Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (n) {")
			.containsOne("case 0:")
			.containsOne("case 1:")
			.containsOne("while (")
			.containsOne("default")
	}
}
