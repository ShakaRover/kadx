package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import org.junit.jupiter.api.Test

/**
 * 条件 2：复杂嵌套条件（null 判断、字段比较、方法调用）不应导致反编译崩溃。
 */
class TestConditions2 : IntegrationTest() {

	@Test
	fun test() {
		getClassNode(TestConditions2Fixture.TestCls::class.java)
	}
}
