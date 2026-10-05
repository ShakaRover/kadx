package kadx.tests.integration.inner

import kadx.api.KadxInternalAccess
import kadx.api.JavaClass
import kadx.core.dex.attributes.AType
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类名中含 `$` 的内部类：不应被识别为匿名类，且全限定名还原正确。
 */
class TestAnonymousClass20 : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestAnonymousClass20Fixture.`Test$Cls`::class.java)
		assertThat(cls.get(AType.ANONYMOUS_CLASS)).isNull()

		val javaClass: JavaClass = KadxInternalAccess.convertClassNode(kadxDecompiler, cls)
		assertThat(javaClass.getTopParentClass()).isEqualTo(javaClass)

		assertThat(cls)
			.code()
			.containsOne("new TestAnonymousClass20Fixture\$Test\$Cls();")
	}
}
