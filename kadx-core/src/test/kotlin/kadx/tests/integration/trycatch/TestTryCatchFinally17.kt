package kadx.tests.integration.trycatch

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * 多个 catch 加 finally：两个异常类型都应保留，且不应合并成 Throwable。
 */
class TestTryCatchFinally17 : SmaliTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11, TestProfile.JAVA8)
	fun test() {
		disableCompilation()
		val node: ClassNode = getClassNode(TestTryCatchFinally17Fixture.TestCls::class.java)
		assertThat(node)
			.code()
			.containsOne("TCls.doSomething()")
			.containsOne("TCls.doFinally()")
			.containsOne("} finally")
			.containsOne("catch (NullPointerException ")
			.containsOne("catch (UnsupportedOperationException ")
			.doesNotContain("catch (Throwable")
	}
}
