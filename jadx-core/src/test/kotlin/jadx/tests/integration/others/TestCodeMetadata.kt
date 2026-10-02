package jadx.tests.integration.others

import jadx.api.JadxInternalAccess
import jadx.api.JavaClass
import jadx.api.JavaMethod
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeAnnotation.AnnType
import jadx.api.metadata.ICodeMetadata
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 代码元数据：字段引用、方法定义位置、向上查找注解等元数据查询应返回正确节点。
 */
class TestCodeMetadata : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestCodeMetadataFixture.TestCls::class.java)
		assertThat(cls).code().containsOne("return a.str;")

		val testMth: MethodNode = getMethod(cls, "test")
		val callMth: MethodNode = getMethod(cls, "call")

		val callDefPos = callMth.getDefPosition()
		assertThat(callDefPos).isNotZero()

		val javaClass: JavaClass = JadxInternalAccess.convertClassNode(jadxDecompiler, cls)
		val callJavaMethod: JavaMethod = JadxInternalAccess.convertMethodNode(jadxDecompiler, callMth)
		val callUsePlaces: List<Int> = javaClass.getUsePlacesFor(javaClass.getCodeInfo(), callJavaMethod)
		assertThat(callUsePlaces).hasSize(1)
		val callUse = callUsePlaces[0]

		val metadata: ICodeMetadata = cls.getCode().getCodeMetadata()
		println(metadata)
		val callDef: ICodeNodeRef? = metadata.getNodeAt(callUse)
		assertThat(callDef).isSameAs(testMth)

		val endPos = AtomicInteger()
		val testEnd = metadata.searchUp(callDefPos) { pos, ann ->
			if (ann.getAnnType() == AnnType.END) {
				endPos.set(pos)
				ann
			} else {
				null
			}
		}
		assertThat(testEnd).isNotNull()
		val testEndPos = endPos.get()

		val closest: ICodeAnnotation? = metadata.getClosestUp(testEndPos)
		assertThat(closest).isInstanceOf(FieldNode::class.java) // field reference from 'return a.str;'

		val nodeBelow: ICodeNodeRef? = metadata.getNodeBelow(testEndPos)
		assertThat(nodeBelow).isSameAs(callMth)
	}
}
