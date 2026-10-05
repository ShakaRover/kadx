package kadx.tests.integration.variables

import kadx.api.ICodeInfo
import kadx.api.metadata.ICodeNodeRef
import kadx.api.metadata.annotations.NodeDeclareRef
import kadx.api.metadata.annotations.VarNode
import kadx.api.utils.CodeUtils
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ArrayList

/**
 * 变量声明注解：方法参数的 [VarNode] 名称应按声明顺序被记录（无调试信息时也能恢复）。
 */
class TestVariablesDeclAnnotation : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		val cls: ClassNode = getClassNode(TestVariablesDeclAnnotationFixture.TestCls::class.java)
		assertThat(cls).code()
			.containsOne("public int test(String str, int i) {")
			.containsOne("public abstract int test2(String str);")

		checkArgNamesInMethod(cls, "test", "[str, i]")
		checkArgNamesInMethod(cls, "test2", "[str]")
	}

	private fun checkArgNamesInMethod(cls: ClassNode, mthName: String, expectedVars: String) {
		val testMthOpt = cls.searchMethodByShortName(mthName)
		assertThat(testMthOpt).isNotNull()
		val testMth: MethodNode = testMthOpt ?: error("method not found: $mthName")

		val codeInfo: ICodeInfo = cls.getCode()
		val mthDefPos = testMth.defPosition
		val lineEndPos = CodeUtils.getLineEndForPos(codeInfo.codeStr, mthDefPos)
		val argNames2 = ArrayList<String?>()
		codeInfo.codeMetadata.searchDown(mthDefPos) { pos, ann ->
			if (pos > lineEndPos) {
				return@searchDown true // 到行尾即停止
			}
			if (ann is NodeDeclareRef) {
				val declRef: ICodeNodeRef = ann.getNode()
				if (declRef is VarNode) {
					if (declRef.getMth() == testMth) {
						argNames2.add(declRef.getName())
					}
				}
			}
			null
		}

		assertThat(argNames2).doesNotContainNull()
		assertThat(argNames2.toString()).isEqualTo(expectedVars)
	}
}
