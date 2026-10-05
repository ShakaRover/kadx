package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 12：多层 if/else if 与嵌套条件，控制流应还原为原始分支结构。
 */
class TestConditions12 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions12Fixture.TestCls::class.java))
			.code()
			.containsOne("if (quality >= 10 && raw != 0) {")
			.containsOne("} else if (raw == 0 || quality < 6 || !qualityReading) {")
			.containsOne("if (quality < 30) {")
			.containsOne("if (quality >= 10) {")
			.containsOne("if (raw > 0) {")
			.containsOne("if (quality >= 30 && autoStop) {")
			.containsOne("if (!autoStop && lastValidRaw > -1 && quality < 10) {")
			.doesNotContain("return")
	}
}
