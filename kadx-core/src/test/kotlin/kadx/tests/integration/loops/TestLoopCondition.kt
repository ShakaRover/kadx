package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 复合循环条件（`!=` 与 `<` 组合）下的循环体还原。
 */
class TestLoopCondition : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopConditionFixture.TestCls::class.java))
			.code()
			.containsOne("list.set(i, \"ABC\")")
			.containsOne("list.set(i, \"DEF\")")
	}
}
