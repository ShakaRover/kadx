package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 13：if / else if / else 链应保留，且不产生多余 return。
 */
class TestConditions13 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions13Fixture.TestCls::class.java))
			.code()
			.containsOne("if (quality >= 10 && raw != 0) {")
			.containsOne("System.out.println(\"OK\" + raw);")
			.containsOne("qualityReading = false;")
			.containsOne("} else if (raw == 0 || quality < 6 || !qualityReading) {")
			.doesNotContain("return")
	}
}
