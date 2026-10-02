package jadx.tests.integration.fallback

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * fallback 模式：DEX 输入下无法反编译的方法应保留原始指令骨架（标签、寄存器赋值）。
 */
class TestFallbackMode : IntegrationTest() {

	@Test
	fun test() {
		useDexInput()
		setFallback()
		disableCompilation()

		assertThat(getClassNode(TestFallbackModeFixture.TestCls::class.java))
			.code()
			.contains("public int test(int r2) {")
			.containsOne("r1 = this;")
			.containsOne("L0:")
			.containsOne("L7:")
			.containsOne("int r2 = r2 + 1")
			.doesNotContain("throw new UnsupportedOperationException")
	}
}
