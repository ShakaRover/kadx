package jadx.tests.integration.rename

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * 用户重命名后成员顺序应按别名稳定排序；有/无调试信息两种输入都要一致。
 */
class TestUserRenamesMemberOrder : IntegrationTest() {

	@ParameterizedTest(name = "debug info added: {0}")
	@CsvSource("true", "false")
	fun test(debugInfo: Boolean) {
		getArgs().isDebugInfo = debugInfo
		addClsRename(TestUserRenamesMemberOrderFixture.TestCls.A::class.java.name, "Zebra")
		addClsRename(TestUserRenamesMemberOrderFixture.TestCls.B::class.java.name, "Alpha")
		addMthRename(TestUserRenamesMemberOrderFixture.TestCls::class.java.name, "first()V", "zeta")
		addMthRename(TestUserRenamesMemberOrderFixture.TestCls::class.java.name, "second()V", "alpha")

		val cls: ClassNode = getClassNode(TestUserRenamesMemberOrderFixture.TestCls::class.java)
		val code = cls.getCode().codeStr
		if (debugInfo) {
			assertThat(code).containsSubsequence("static int z =", "static int a =")
			assertThat(code).containsSubsequence("(Zebra ", "(Alpha ", "(Zebra[] ", "(Alpha[] ")
			assertThat(code).containsSubsequence("void zeta()", "void alpha()")
		} else {
			assertThat(code).containsSubsequence("static int a =", "static int z =")
			assertThat(code).containsSubsequence("(Alpha ", "(Zebra ", "(Alpha[] ", "(Zebra[] ")
			assertThat(code).containsSubsequence("void alpha()", "void zeta()")
		}
		assertThat(cls).reloadCode(this).isEqualTo(code)
	}
}
