package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * switch 中直接 return：空 case 5 应被移除。
 */
class TestSwitchReturnFromCase : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSwitchReturnFromCaseFixture.TestCls::class.java))
			.code()
			.contains("switch (a % 10) {")
			// case 5: removed
			.countString(5, "case ")
			.countString(3, "break;")
			.containsOne("s = \"1\";")
			.containsOne("s = \"2\";")
			.containsOne("s = \"4\";")
			.containsOne("s = \"5\";")
	}
}
