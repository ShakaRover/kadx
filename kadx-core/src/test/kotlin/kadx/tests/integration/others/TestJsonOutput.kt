package kadx.tests.integration.others

import kadx.api.KadxArgs.OutputFormatEnum.JSON
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * JSON 输出格式：偏移量与内部类声明应正确输出（含 fallback 模式）。
 */
class TestJsonOutput : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		args.outputFormat = JSON

		KadxAssertions.assertThat(getClassNode(TestJsonOutputFixture.TestCls::class.java))
			.code()
			.contains("\"offset\": \"0x")
			.containsOne("public static class Inner implements Runnable")
	}

	@Test
	fun testFallback() {
		disableCompilation()
		setFallback()
		args.outputFormat = JSON

		KadxAssertions.assertThat(getClassNode(TestJsonOutputFixture.TestCls::class.java))
			.code()
			.contains("\"offset\": \"0x")
			.containsOne("public static class Inner implements java.lang.Runnable")
	}
}
