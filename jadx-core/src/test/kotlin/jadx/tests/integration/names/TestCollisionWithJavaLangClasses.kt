package jadx.tests.integration.names

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import jadx.tests.integration.names.pkg2.System as Pkg2System
import jadx.tests.integration.names.pkg2.TestCls as Pkg2TestCls

/**
 * 与 `java.lang.System` 冲突：内部类 `System` 的调用应保留短名，
 * 而对 `java.lang.System` 的引用必须使用全限定名。
 */
class TestCollisionWithJavaLangClasses : IntegrationTest() {

	@Test
	fun test1() {
		assertThat(getClassNode(TestCollisionWithJavaLangClassesFixture.TestCls1::class.java))
			.code()
			.containsOne("java.lang.System.out.println")
	}

	@Test
	fun test2() {
		assertThat(getClassNode(TestCollisionWithJavaLangClassesFixture.TestCls2::class.java))
			.code()
			.containsLine(2, "System.doSomething();")
			.containsOne("java.lang.System.out.println")
	}

	@Test
	fun test3() {
		val classes = getClassNodes(
			Pkg2System::class.java,
			Pkg2TestCls::class.java,
		)
		assertThat(searchCls(classes, "TestCls"))
			.code()
			.containsLine(2, "System.doSomething();")
			.containsOne("java.lang.System.out.println")
	}
}
