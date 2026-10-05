package kadx.tests.integration.others

import kadx.api.ICodeInfo
import kadx.api.JavaClass
import kadx.api.JavaVariable
import kadx.api.metadata.annotations.VarNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 代码元数据中的变量引用：方法参数变量的使用位置应能被正确收集。
 */
class TestCodeMetadata3 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		val cls: ClassNode = getClassNode(TestCodeMetadata3Fixture.TestCls::class.java)
		val codeInfo: ICodeInfo = cls.getCode()
		println(codeInfo.codeMetadata)

		val testMth: MethodNode = getMethod(cls, "test")
		val javaClass: JavaClass = toJavaClass(cls)
		val varNodes: List<VarNode> = testMth.collectArgNodes()
		assertThat(varNodes).hasSize(1)
		val strVar = varNodes[0]
		val strJavaVar: JavaVariable = toJavaVariable(strVar)
		assertThat(strJavaVar.getName()).isEqualTo("str")

		val strUsePlaces: List<Int> = javaClass.getUsePlacesFor(codeInfo, strJavaVar)
		assertThat(strUsePlaces).hasSize(2)
		assertThat(codeInfo).code().countString(3, "str")
	}
}
