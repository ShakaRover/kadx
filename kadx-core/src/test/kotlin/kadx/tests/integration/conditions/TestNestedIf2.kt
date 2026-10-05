package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 嵌套 if 2：`&&` 条件与嵌套提前返回应还原为原始结构，不产生 else。
 */
class TestNestedIf2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestNestedIf2Fixture.TestCls::class.java))
			.code()
			.containsOne("if (executedCount != repeatCount && isRun(delta, object)) {")
			.containsOne("if (finished) {")
			.doesNotContain("else")
	}
}
