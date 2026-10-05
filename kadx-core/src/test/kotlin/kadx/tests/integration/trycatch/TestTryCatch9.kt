package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * try 中赋值给 Integer 变量：应保留 res 的 null 初始化与 catch 分支。
 */
class TestTryCatch9 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		assertThat(getClassNode(TestTryCatch9Fixture.TestCls::class.java))
			.code()
			.containsOne("logError(ex);")
			.containsOne("Integer res")
			.contains("res = null;")
	}
}
