package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
