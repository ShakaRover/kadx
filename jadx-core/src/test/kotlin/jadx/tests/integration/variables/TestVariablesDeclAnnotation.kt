package jadx.tests.integration.variables

import jadx.api.ICodeInfo
import jadx.api.metadata.ICodeNodeRef
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.api.metadata.annotations.VarNode
import jadx.api.utils.CodeUtils
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
		val mthDefPos = testMth.getDefPosition()
		val lineEndPos = CodeUtils.getLineEndForPos(codeInfo.getCodeStr(), mthDefPos)
		val argNames2 = ArrayList<String?>()
		codeInfo.getCodeMetadata().searchDown(mthDefPos) { pos, ann ->
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
