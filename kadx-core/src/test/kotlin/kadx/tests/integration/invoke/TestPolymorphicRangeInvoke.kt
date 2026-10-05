package kadx.tests.integration.invoke

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat

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
