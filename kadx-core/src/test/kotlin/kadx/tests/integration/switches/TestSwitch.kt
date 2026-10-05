package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字符 switch：`'.'` 与 `'/'` 共用分支，反编译后应保留 `case '/'` 和 `default`。
 */
class TestSwitch : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchFixture.TestCls::class.java))
			.code()
			.contains("case '/':")
			.contains(indent(5) + "break;")
			.contains(indent(4) + "default:")
			.containsOne("i++")
			.countString(4, "break;")
	}
}
