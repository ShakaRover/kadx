package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * finally 块中再次判空并调用清理方法：两处 `!= null` 判断都应保留。
 */
class TestTryCatchFinally14 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11, TestProfile.JAVA8)
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally14Fixture.TestCls::class.java))
			.code()
			.containsOne(".doSomething();")
			.containsOne("} finally {")
			.containsOne(".doFinally();")
			.countString(2, "!= null) {")
	}
}
