package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.SmaliTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 多 catch + finally + 后续条件分支：finally 与两个异常类型都应保留。
 */
class TestTryCatchFinally18 : SmaliTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11)
	fun test() {
		disableCompilation()
		val node: ClassNode = getClassNode(TestTryCatchFinally18Fixture.TestCls::class.java)
		assertThat(node)
			.code()
			.containsOne("TCls.doSomething()")
			.containsOne("TCls.dispose()")
			.containsOne("} finally")
			.containsOne("catch (NullPointerException ")
			.containsOne("catch (UnsupportedOperationException ")
			.doesNotContain("catch (Throwable")
	}

	@NotYetImplemented("To be investigated why J8 does not work")
	@TestWithProfiles(TestProfile.JAVA8)
	fun testJ8() {
		disableCompilation()
		val node: ClassNode = getClassNode(TestTryCatchFinally18Fixture.TestCls::class.java)
		assertThat(node)
			.code()
			.containsOne("TCls.doSomething()")
			.containsOne("TCls.dispose()")
			.containsOne("} finally")
			.containsOne("catch (NullPointerException ")
			.containsOne("catch (UnsupportedOperationException ")
			.doesNotContain("catch (Throwable")
	}
}
