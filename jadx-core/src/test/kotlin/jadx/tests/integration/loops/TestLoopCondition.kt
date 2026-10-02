package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 复合循环条件（`!=` 与 `<` 组合）下的循环体还原。
 */
class TestLoopCondition : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestLoopConditionFixture.TestCls::class.java))
			.code()
			.containsOne("list.set(i, \"ABC\")")
			.containsOne("list.set(i, \"DEF\")")
	}
}
