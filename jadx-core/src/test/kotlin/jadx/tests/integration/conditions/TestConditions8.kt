package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 条件 8：多个提前返回后再执行操作，`showMore()` 调用应被保留。
 */
class TestConditions8 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestConditions8Fixture.TestCls::class.java))
			.code()
			.contains("showMore();")
	}
}
