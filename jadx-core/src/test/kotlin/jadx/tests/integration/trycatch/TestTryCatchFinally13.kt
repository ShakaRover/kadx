package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * try/catch/finally 中提前 return 与多分支 if 的组合应保留 finally。
 */
class TestTryCatchFinally13 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally13Fixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
	}
}
