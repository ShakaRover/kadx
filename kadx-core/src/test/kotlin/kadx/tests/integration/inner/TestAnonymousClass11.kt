package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类捕获 final 局部变量：捕获变量应作为普通局部变量使用，不出现 synthetic 字段。
 */
class TestAnonymousClass11 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass11Fixture.TestCls::class.java))
			.code()
			.containsOne("System.out.println(\"a\" + a);")
			.containsOne("print(a);")
			.doesNotContain("synthetic")
	}
}
