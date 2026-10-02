package jadx.tests.integration.trycatch

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * catch 中的异常变量重命名：有无调试信息时应分别得到 ex/e2。
 */
class TestTryCatch7 : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestTryCatch7Fixture.TestCls::class.java)
		val code: String = cls.getCode().toString()
		check(code, "e", "ex")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		val cls: ClassNode = getClassNode(TestTryCatch7Fixture.TestCls::class.java)
		val code: String = cls.getCode().toString()
		check(code, "e", "e2")
	}

	private fun check(code: String, excVarName: String, catchExcVarName: String) {
		assertThat(code)
			.containsOne("Exception " + excVarName + " = new Exception();")
			.containsOne("} catch (Exception " + catchExcVarName + ") {")
			.containsOne(excVarName + " = " + catchExcVarName + ';')
			.containsOne(excVarName + ".printStackTrace();")
			.containsOne("return " + excVarName + ';')
	}
}
