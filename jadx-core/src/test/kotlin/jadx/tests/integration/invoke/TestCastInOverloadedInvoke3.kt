package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 对“未知但存在重载”的方法调用：null 参数需保留 `(String)` 转换。
 */
class TestCastInOverloadedInvoke3 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNode(TestCastInOverloadedInvoke3Fixture.TestCls::class.java))
			.code()
			.containsOne("OuterCls.call((String) null);")
	}
}
