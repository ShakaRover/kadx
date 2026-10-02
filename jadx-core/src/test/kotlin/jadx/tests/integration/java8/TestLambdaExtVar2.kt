package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

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
