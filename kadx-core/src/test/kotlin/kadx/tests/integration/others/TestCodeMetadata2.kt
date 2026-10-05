package kadx.tests.integration.others

import kadx.api.KadxInternalAccess.convertClassNode
import kadx.api.KadxInternalAccess.convertMethodNode
import kadx.api.JavaClass
import kadx.api.JavaMethod
import kadx.api.metadata.ICodeMetadata
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 代码元数据：方法调用处（use place）应映射到调用方方法节点。
 */
class TestCodeMetadata2 : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestCodeMetadata2Fixture.TestCls::class.java)
		assertThat(cls).code().containsOne("return empty();")

		val testMth: MethodNode = getMethod(cls, "test")
		val emptyMth: MethodNode = getMethod(cls, "empty")

		val javaClass: JavaClass = convertClassNode(kadxDecompiler, cls)
		val emptyJavaMethod: JavaMethod = convertMethodNode(kadxDecompiler, emptyMth)
		val emptyUsePlaces: List<Int> = javaClass.getUsePlacesFor(javaClass.getCodeInfo(), emptyJavaMethod)
		assertThat(emptyUsePlaces).hasSize(1)
		val callUse = emptyUsePlaces[0]

		val metadata: ICodeMetadata = cls.getCode().codeMetadata
		assertThat(metadata.getNodeAt(callUse)).isSameAs(testMth)
	}
}
