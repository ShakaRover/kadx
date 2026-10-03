package jadx.tests.integration.debuginfo

import jadx.api.utils.CodeUtils
import jadx.core.dex.attributes.nodes.LineAttrNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证字段/方法/内部类的源码行号与反编译输出行号对应关系。
 *
 * fixture 文件通过 `//` 注释填充，保证 `TestCls` 与原始文件处于相同的行号，
 * 因此下面断言中的绝对行号保持不变。
 */
class TestLineNumbers : IntegrationTest() {

	@Test
	fun test() {
		printLineNumbers()
		val cls = getClassNode(TestLineNumbersFixture.TestCls::class.java)
		val code = cls.getCode().toString()

		val field = checkNotNull(cls.searchFieldByName("field"))
		val func = checkNotNull(cls.searchMethodByShortId("func()V"))
		val inner = cls.innerClasses[0]
		val innerFunc = checkNotNull(inner.searchMethodByShortId("innerFunc()V"))
		val innerFunc2 = checkNotNull(inner.searchMethodByShortId("innerFunc2()V"))
		val innerFunc3 = checkNotNull(inner.searchMethodByShortId("innerFunc3()V"))
		val innerField = checkNotNull(inner.searchFieldByName("innerField"))

		// 校验源码行号（仅指令与方法可用）
		val testClassLine = 16
		assertThat(testClassLine + 3).isEqualTo(func.sourceLine)
		assertThat(testClassLine + 9).isEqualTo(innerFunc.sourceLine)
		assertThat(testClassLine + 12).isEqualTo(innerFunc2.sourceLine)
		assertThat(testClassLine + 20).isEqualTo(innerFunc3.sourceLine)

		// 校验反编译输出行
		checkLine(code, field, "int field;")
		checkLine(code, func, "public void func() {")
		checkLine(code, inner, "public static class Inner {")
		checkLine(code, innerField, "int innerField;")
		checkLine(code, innerFunc, "public void innerFunc() {")
		checkLine(code, innerFunc2, "public void innerFunc2() {")
		checkLine(code, innerFunc3, "public void innerFunc3() {")
	}

	private fun checkLine(code: String, node: LineAttrNode, str: String) {
		val line = CodeUtils.getLineForPos(code, node.defPosition)
		assertThat(line).contains(str)
	}
}
