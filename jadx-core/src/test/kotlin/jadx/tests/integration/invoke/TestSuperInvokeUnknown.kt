package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
