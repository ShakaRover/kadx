package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
