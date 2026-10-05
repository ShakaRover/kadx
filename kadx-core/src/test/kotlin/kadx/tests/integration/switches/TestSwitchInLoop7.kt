package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 检查冗余的 default continue 分支不会被还原出来。
 */
class TestSwitchInLoop7 : IntegrationTest() {

	@Test
	fun test() {
		// Checks that the redundant default continue case is not recovered
		assertThat(getClassNode(TestSwitchInLoop7Fixture.TestCls::class.java))
			.code()
			.containsOne("switch (n) {")
			.containsOne("case 0:")
			.containsOne("case 1:")
			.containsOne("while (")
			.doesNotContain("default")
			.doesNotContain("contine")
	}
}
