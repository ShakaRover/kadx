package kadx.tests.integration.java8

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

/**
 * lambda 捕获的外部方法参数（`str`）应被内联还原，不残留合成 `lambda$` 方法。
 */
class TestLambdaExtVar : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8)
	fun test() {
		val cls: ClassNode = getClassNode(TestLambdaExtVarFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.doesNotContain("lambda$")
			.containsOne("return s.equals(str);") // TODO: simplify to expression
	}
}
