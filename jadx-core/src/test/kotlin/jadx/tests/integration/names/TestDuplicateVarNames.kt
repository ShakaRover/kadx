package jadx.tests.integration.names

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
