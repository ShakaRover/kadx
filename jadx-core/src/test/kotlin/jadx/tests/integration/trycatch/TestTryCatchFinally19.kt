package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.SmaliTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 当所有处理器都不重新抛出异常时的 finally 提取（当前仅处理会重新抛出的情况，已知未实现）。
 */
class TestTryCatchFinally19 : SmaliTest() {

	@TestWithProfiles(TestProfile.D8_J11)
	@NotYetImplemented("Currently only processing finally blocks if the all handler throws.")
	fun testDXJ8() {
		disableCompilation()
		val node: ClassNode = getClassNode(TestTryCatchFinally19Fixture.TestCls::class.java)
		assertThat(node)
			.code()
			.containsOne("TCls.doSomething()")
			.containsOne("TCls.dispose()")
			.containsOne("} finally")
			.containsOne("catch (Throwable ")
			.containsOne("return null")
	}
}
