package jadx.tests.integration.fallback

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * fallback 模式下存在大量 nop 填充：应正常生成代码，而不是输出 "Method dump skipped"。
 */
class TestFallbackManyNops : SmaliTest() {

	@Test
	fun test() {
		setFallback()
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.contains("public static void test() {")
			.containsOne("return")
			.doesNotContain("Method dump skipped")
	}
}
