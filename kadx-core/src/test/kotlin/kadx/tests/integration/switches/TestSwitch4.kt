package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * switch 贯穿（fallthrough）解析十进制数：不应生成 break。
 */
class TestSwitch4 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitch4Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (")
			.countString(3, "case ")
			.doesNotContain("break")
	}
}
