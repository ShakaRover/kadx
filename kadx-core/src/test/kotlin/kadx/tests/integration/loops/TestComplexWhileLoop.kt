package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 复杂 while 条件（条件里带赋值）不应被误还原为 for 循环。
 */
class TestComplexWhileLoop : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestComplexWhileLoopFixture.TestCls::class.java))
			.code()
			.doesNotContain("for (int at = 0; at < len; at = endAt) {")
	}
}
