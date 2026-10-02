package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
