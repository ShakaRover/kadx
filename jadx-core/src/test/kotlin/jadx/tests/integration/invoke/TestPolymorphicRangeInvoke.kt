package jadx.tests.integration.invoke

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * `invoke-polymorphic/range` 指令还原（参数超过 5 个时使用 range 形式）。
 */
class TestPolymorphicRangeInvoke : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8)
	fun test() {
		val cls: ClassNode = getClassNode(TestPolymorphicRangeInvokeFixture.TestCls::class.java)
		assertThat(cls).code()
			.containsOne("return (String) methodHandle.invoke(this, 10, 20, 30, 40, 50, 60);")
		assertThat(cls).disasmCode()
			.containsOne("invoke-polymorphic/range")
	}
}
