package jadx.tests.integration.java8

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * D8 desugar 后的 lambda 应被反糖化（resugar），不残留 `lambda$` 合成方法。
 */
class TestLambdaResugar : IntegrationTest() {

	@NotYetImplemented("Inline lambda methods")
	@TestWithProfiles(TestProfile.D8_J11_DESUGAR)
	fun test() {
		assertThat(getClassNode(TestLambdaResugarFixture.TestCls::class.java))
			.code()
			.doesNotContain("lambda$")
	}
}
