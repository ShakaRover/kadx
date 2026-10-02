package jadx.tests.integration.others

import jadx.api.JadxArgs.OutputFormatEnum.JSON
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * JSON 输出格式：偏移量与内部类声明应正确输出（含 fallback 模式）。
 */
class TestJsonOutput : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		args.outputFormat = JSON

		JadxAssertions.assertThat(getClassNode(TestJsonOutputFixture.TestCls::class.java))
			.code()
			.contains("\"offset\": \"0x")
			.containsOne("public static class Inner implements Runnable")
	}

	@Test
	fun testFallback() {
		disableCompilation()
		setFallback()
		args.outputFormat = JSON

		JadxAssertions.assertThat(getClassNode(TestJsonOutputFixture.TestCls::class.java))
			.code()
			.contains("\"offset\": \"0x")
			.containsOne("public static class Inner implements java.lang.Runnable")
	}
}
