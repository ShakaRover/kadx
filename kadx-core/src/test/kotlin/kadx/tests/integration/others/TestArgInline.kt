package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 参数内联：`a = a + 1` 形式应被优化为自增 `i++`。
 */
class TestArgInline : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestArgInlineFixture.TestCls::class.java))
			.code()
			.contains("i++;")
			.doesNotContain("i = i + 1;")
	}
}
