package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

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
