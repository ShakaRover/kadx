package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * else-if 代码风格：多个 else-if 链不应出现空的 then 块或取反的字符串比较。
 */
class TestElseIfCodeStyle : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestElseIfCodeStyleFixture.TestCls::class.java))
			.code()
			.doesNotContain("!\"c\".equals(str)")
			.doesNotContain("{\n" + indent(2) + "} else {") // no empty `then` block
	}
}
