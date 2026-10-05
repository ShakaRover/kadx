package kadx.tests.integration.others

import kadx.api.KadxInternalAccess
import kadx.api.JavaClass
import kadx.api.JavaMethod
import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeAnnotation.AnnType
import kadx.api.metadata.ICodeMetadata
import kadx.api.metadata.ICodeNodeRef
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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

		val callDefPos = callMth.defPosition
		assertThat(callDefPos).isNotZero()

		val javaClass: JavaClass = KadxInternalAccess.convertClassNode(kadxDecompiler, cls)
		val callJavaMethod: JavaMethod = KadxInternalAccess.convertMethodNode(kadxDecompiler, callMth)
		val callUsePlaces: List<Int> = javaClass.getUsePlacesFor(javaClass.getCodeInfo(), callJavaMethod)
		assertThat(callUsePlaces).hasSize(1)
		val callUse = callUsePlaces[0]

		val metadata: ICodeMetadata = cls.getCode().codeMetadata
		println(metadata)
		val callDef: ICodeNodeRef? = metadata.getNodeAt(callUse)
		assertThat(callDef).isSameAs(testMth)

		val endPos = AtomicInteger()
		val testEnd = metadata.searchUp(callDefPos) { pos, ann ->
			if (ann.annType == AnnType.END) {
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
