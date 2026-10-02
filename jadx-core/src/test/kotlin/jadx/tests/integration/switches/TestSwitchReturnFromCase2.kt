package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * case 落空后统一 return true。
 */
class TestSwitchReturnFromCase2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSwitchReturnFromCase2Fixture.TestCls::class.java))
			.code()
			.contains("switch (a % 4) {")
	}
}
