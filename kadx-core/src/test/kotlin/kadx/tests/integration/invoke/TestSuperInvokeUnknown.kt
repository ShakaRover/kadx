package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 父类未知时的 `super.doSomething()` 调用：单类与整体反编译都应保留。
 */
class TestSuperInvokeUnknown : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		noDebugInfo()
		assertThat(getClassNode(TestSuperInvokeUnknownFixture.TestCls.NestedClass::class.java)) // BaseClass unknown
			.code()
			.containsOne("return super.doSomething();")
	}

	@Test
	fun testTopCls() {
		noDebugInfo()
		assertThat(getClassNode(TestSuperInvokeUnknownFixture.TestCls::class.java))
			.code()
			.containsOne("return super.doSomething();")
	}
}
