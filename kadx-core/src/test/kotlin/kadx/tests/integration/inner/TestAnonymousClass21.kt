package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类不应把常量字符串内联进构造函数参数（保持变量引用）。
 */
class TestAnonymousClass21 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestAnonymousClass21Fixture.TestCls::class.java))
			.code()
			.containsOne("String str = \"str\";")
			.containsOne("System.out.println(str);")
	}
}
