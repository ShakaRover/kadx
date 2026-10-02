package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类使用外部类类型变量：应还原为 `Outer<String>.Inner` 与 `Map.Entry<String, String>`。
 */
class TestTypeVarsFromOuterClass : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeVarsFromOuterClassFixture.TestCls::class.java))
			.code()
			.doesNotContain("Outer<Y>.Inner inner")
			.doesNotContain("Object entry = ")
			.countString(2, "Outer<String>.Inner inner = this.outer.getInner();")
			.countString(2, "Map.Entry<String, String> entry = ")
	}
}
