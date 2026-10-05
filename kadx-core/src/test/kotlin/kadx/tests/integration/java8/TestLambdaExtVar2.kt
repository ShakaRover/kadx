package kadx.tests.integration.java8

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * lambda 捕获的外部局部变量（`space`）应被内联还原，不残留合成 `lambda$` 方法。
 */
class TestLambdaExtVar2 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11, TestProfile.JAVA11)
	fun test() {
		assertThat(getClassNode(TestLambdaExtVar2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("lambda$")
			.containsOne("String space = \" \";")
			.containsOne("s.equals(space) || s.contains(space)")
	}
}
