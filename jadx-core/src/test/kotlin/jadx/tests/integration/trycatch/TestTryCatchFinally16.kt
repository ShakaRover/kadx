package jadx.tests.integration.trycatch

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.SmaliTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 空 catch 与 finally 组合：finally 中的清理调用应保留，catch 参数类型为 Exception。
 */
class TestTryCatchFinally16 : SmaliTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11, TestProfile.JAVA8)
	fun test() {
		disableCompilation()
		val node: ClassNode = getClassNode(TestTryCatchFinally16Fixture.TestCls::class.java)
		assertThat(node)
			.code()
			.containsOne("TCls.doSomething()")
			.containsOne("TCls.doFinally()")
			.containsOne("finally")
			.containsOne("} catch")
			.contains("catch (Exception e)")
	}
}
