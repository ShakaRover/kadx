package kadx.tests.integration.trycatch

import kadx.NotYetImplemented
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
