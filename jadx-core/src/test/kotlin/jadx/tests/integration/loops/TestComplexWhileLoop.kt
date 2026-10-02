package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
