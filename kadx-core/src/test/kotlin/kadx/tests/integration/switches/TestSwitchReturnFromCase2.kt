package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * case 落空后统一 return true。
 */
class TestSwitchReturnFromCase2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSwitchReturnFromCase2Fixture.TestCls::class.java))
			.code()
			.contains("switch (a % 4) {")
	}
}
