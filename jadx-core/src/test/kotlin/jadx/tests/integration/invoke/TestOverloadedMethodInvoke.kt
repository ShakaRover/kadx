package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 重载方法（Throwable/Exception 参数）：仅保留必要的一处 `(Throwable)` 转换。
 */
class TestOverloadedMethodInvoke : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestOverloadedMethodInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("public void test(Throwable th, Exception e) {")
			.containsOne("method(e, 10);")
			.containsOne("method(th, 100);")
			.containsOne("method((Throwable) e, 1000);")
			.containsOne("method((Exception) th, 10000);")
			.doesNotContain("(Exception) e")
	}
}
