package jadx.tests.integration.inner

import jadx.api.JadxInternalAccess
import jadx.api.JavaClass
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类名中含 `$` 的内部类：不应被识别为匿名类，且全限定名还原正确。
 */
class TestAnonymousClass20 : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestAnonymousClass20Fixture.`Test$Cls`::class.java)
		assertThat(cls.get(AType.ANONYMOUS_CLASS)).isNull()

		val javaClass: JavaClass = JadxInternalAccess.convertClassNode(jadxDecompiler, cls)
		assertThat(javaClass.getTopParentClass()).isEqualTo(javaClass)

		assertThat(cls)
			.code()
			.containsOne("new TestAnonymousClass20Fixture\$Test\$Cls();")
	}
}
