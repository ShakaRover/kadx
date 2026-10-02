package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 接口继承类型还原：分支赋值给 `IBase` 的局部变量应保留接口类型与分支内赋值。
 */
class TestTypeInheritance : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeInheritanceFixture.TestCls::class.java))
			.code()
			.containsOne("IBase impl;")
			.containsOne("impl = new A();")
			.containsOne("B b = new B();")
			.containsOne("impl = b;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestTypeInheritanceFixture.TestCls::class.java)
	}
}
