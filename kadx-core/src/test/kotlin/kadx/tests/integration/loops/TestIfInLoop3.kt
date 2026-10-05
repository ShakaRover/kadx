package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环内的多重 if/else 与索引计算应还原为 for 循环。
 */
class TestIfInLoop3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIfInLoop3Fixture.TestCls::class.java))
			.code()
			.containsOne("for (int i = 0; i < extraArray.length; i += 2) {")
			.containsOne("if (extraArray != null && placingStone) {")
	}
}
