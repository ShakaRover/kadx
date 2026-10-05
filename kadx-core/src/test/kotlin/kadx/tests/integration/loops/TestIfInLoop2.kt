package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套循环中带 break 的索引推进不应被误还原为 for 循环。
 */
class TestIfInLoop2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIfInLoop2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("for (int at = 0; at < len; at = endAt) {")
	}
}
