package kadx.tests.integration.deobf

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类字段类型：开启混淆还原后，内部类泛型类型不应被错误重命名为 `.I` 之类。
 */
class TestFieldFromInnerClass : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		enableDeobfuscation()

		val cls = getClassNode(TestFieldFromInnerClassFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.doesNotContain("class I {")
			.doesNotContain(".I ")
			.doesNotContain(".I.X>")
	}
}
