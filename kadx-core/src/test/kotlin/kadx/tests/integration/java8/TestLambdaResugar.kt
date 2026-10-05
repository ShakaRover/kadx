package kadx.tests.integration.java8

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
