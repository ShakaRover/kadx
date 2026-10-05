package kadx.tests.integration.annotations

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 注解使用关系：注解被哪些类 / 方法引用（use-in / use-in-method）应正确统计。
 */
class TestAnnotationsUsage : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestAnnotationsUsageFixture.TestCls::class.java)
		val annCls = searchCls(cls.innerClasses, "A")
		val bCls = searchCls(cls.innerClasses, "B")
		val cCls = searchCls(cls.innerClasses, "C")
		val testMth = getMethod(cls, "test")
		val testMth2 = getMethod(cls, "test2")

		org.assertj.core.api.Assertions.assertThat(annCls.useIn).contains(cls, bCls, cCls)
		org.assertj.core.api.Assertions.assertThat(annCls.useInMth).contains(testMth, testMth2)

		org.assertj.core.api.Assertions.assertThat(bCls.useIn).contains(cCls)
		org.assertj.core.api.Assertions.assertThat(bCls.useInMth).contains(testMth, testMth2)

		assertThat(cls)
			.code()
			.countString(3, "@A(c = B.class)")
	}
}
