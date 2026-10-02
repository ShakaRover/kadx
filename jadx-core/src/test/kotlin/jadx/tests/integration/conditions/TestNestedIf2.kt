package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套 if 2：`&&` 条件与嵌套提前返回应还原为原始结构，不产生 else。
 */
class TestNestedIf2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestNestedIf2Fixture.TestCls::class.java))
			.code()
			.containsOne("if (executedCount != repeatCount && isRun(delta, object)) {")
			.containsOne("if (finished) {")
			.doesNotContain("else")
	}
}
