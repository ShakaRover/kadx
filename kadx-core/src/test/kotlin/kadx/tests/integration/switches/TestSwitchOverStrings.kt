package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字符串 switch（含哈希冲突的 'frewhyh'/'phgafkp'/'ucguedt'）：应按内容分派。
 */
class TestSwitchOverStrings : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchOverStringsFixture.TestCls::class.java))
			.code()
			.doesNotContain("case -603257287:")
			.doesNotContain("c = ")
			.doesNotContainSubsequence("default:", "case ")
			.containsOne("case \"frewhyh\":")
			.countString(5, "return ")
	}
}
