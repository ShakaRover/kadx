package jadx.tests.integration.java8

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

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
