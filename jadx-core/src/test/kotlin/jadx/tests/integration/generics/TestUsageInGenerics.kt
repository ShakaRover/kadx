package jadx.tests.integration.generics

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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

		JadxAssertions.assertThat(cls)
			.code()
			.containsOne("public <T extends A> T test() {")
	}
}
