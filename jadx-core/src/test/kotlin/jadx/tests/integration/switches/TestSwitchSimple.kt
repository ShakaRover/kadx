package jadx.tests.integration.switches

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 简单 int switch：不应对取模表达式重复加括号。
 */
class TestSwitchSimple : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchSimpleFixture.TestCls::class.java))
			.code()
			.countString(5, "break;")
			.containsOne("System.out.println(s);")
			.containsOne("System.out.println(\"Not Reach\");")
			.doesNotContain("switch ((a % 4)) {")
			.contains("switch (a % 4) {")
	}
}
