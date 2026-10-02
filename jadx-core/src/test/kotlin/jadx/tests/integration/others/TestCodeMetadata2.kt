package jadx.tests.integration.others

import jadx.api.JadxInternalAccess.convertClassNode
import jadx.api.JadxInternalAccess.convertMethodNode
import jadx.api.JavaClass
import jadx.api.JavaMethod
import jadx.api.metadata.ICodeMetadata
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

		val javaClass: JavaClass = convertClassNode(jadxDecompiler, cls)
		val emptyJavaMethod: JavaMethod = convertMethodNode(jadxDecompiler, emptyMth)
		val emptyUsePlaces: List<Int> = javaClass.getUsePlacesFor(javaClass.getCodeInfo(), emptyJavaMethod)
		assertThat(emptyUsePlaces).hasSize(1)
		val callUse = emptyUsePlaces[0]

		val metadata: ICodeMetadata = cls.getCode().getCodeMetadata()
		assertThat(metadata.getNodeAt(callUse)).isSameAs(testMth)
	}
}
