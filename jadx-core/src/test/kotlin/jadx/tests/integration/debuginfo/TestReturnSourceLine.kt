package jadx.tests.integration.debuginfo

import jadx.api.ICodeInfo
import jadx.api.utils.CodeUtils
import jadx.core.dex.attributes.nodes.LineAttrNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 每个 `return` 指令的源码行号映射。
 *
 * fixture 通过 `//` 注释填充保持 `TestCls` 的原始行号，断言全部基于相对偏移。
 */
class TestReturnSourceLine : IntegrationTest() {

	@Test
	fun test() {
		printLineNumbers()

		val cls = getClassNode(TestReturnSourceLineFixture.TestCls::class.java)
		val codeInfo = cls.getCode()
		val lines = codeInfo.getCodeStr().split(Regex("\\R"))

		val test1 = checkNotNull(cls.searchMethodByShortId("test1(Z)I"))
		checkLine(lines, codeInfo, test1, 3, "return 1;")

		val test2 = checkNotNull(cls.searchMethodByShortId("test2(I)I"))
		checkLine(lines, codeInfo, test2, 3, "return v - 1;")
		checkLine(lines, codeInfo, test2, 6, "return v + 1;")

		val test3 = checkNotNull(cls.searchMethodByShortId("test3(I)I"))
		if (isJavaInput()) { // dx 丢失了此 return 的行号
			checkLine(lines, codeInfo, test3, 3, "return v;")
		}
		checkLine(lines, codeInfo, test3, 6, "return v + 1;")
	}

	private fun checkLine(lines: List<String>, cw: ICodeInfo, node: LineAttrNode, offset: Int, str: String) {
		val nodeDefLine = CodeUtils.getLineNumForPos(cw.getCodeStr(), node.getDefPosition(), "\n")
		val decompiledLine = nodeDefLine + offset
		assertThat(lines[decompiledLine - 1]).containsOne(str)
		val sourceLine = cw.getCodeMetadata().getLineMapping()[decompiledLine]
		assertThat(sourceLine).isNotNull()
		assertThat(checkNotNull(sourceLine)).isEqualTo(node.getSourceLine() + offset)
	}
}
