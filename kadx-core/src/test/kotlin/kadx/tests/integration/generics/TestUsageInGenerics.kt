package kadx.tests.integration.generics

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型的使用关系（use-in）：`A` 应被 `B`/`C` 及泛型方法引用。
 */
class TestUsageInGenerics : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestUsageInGenericsFixture.TestCls::class.java)
		val testCls = searchCls(cls.innerClasses, "A")
		val bCls = searchCls(cls.innerClasses, "B")
		val cCls = searchCls(cls.innerClasses, "C")
		val testMth = getMethod(cls, "test")

		org.assertj.core.api.Assertions.assertThat(testCls.useIn).contains(cls, bCls, cCls)
		org.assertj.core.api.Assertions.assertThat(testCls.useInMth).contains(testMth)

		KadxAssertions.assertThat(cls)
			.code()
			.containsOne("public <T extends A> T test() {")
	}
}
