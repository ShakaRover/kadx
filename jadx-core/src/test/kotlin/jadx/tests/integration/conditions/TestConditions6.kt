package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 6：`if` 只做副作用时，返回值判断应直接返回，不生成 else。
 */
class TestConditions6 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions6Fixture.TestCls::class.java))
			.code()
			.contains("return l1.size() == 0;")
			.doesNotContain("else")
	}
}
