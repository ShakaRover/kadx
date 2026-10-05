package kadx.tests.integration.names

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类方法参数名冲突：内部类与匿名类的方法参数重名时不应误用外层参数，
 * 也不应生成匿名类名字。
 */
class TestDuplicateVarNames : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		val cls = getClassNode(TestDuplicateVarNamesFixture.TestCls::class.java)

		assertThat(cls)
			.code()
			.doesNotContain("return a + \".\" + a;")
			.doesNotContain("AnonymousClass1")
	}
}
