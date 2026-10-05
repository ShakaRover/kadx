package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 6：`if` 只做副作用时，返回值判断应直接返回，不生成 else。
 */
class TestConditions6 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestConditions6Fixture.TestCls::class.java))
			.code()
			.contains("return l1.size() == 0;")
			.doesNotContain("else")
	}
}
