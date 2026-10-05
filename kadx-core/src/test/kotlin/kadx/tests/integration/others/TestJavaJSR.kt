package kadx.tests.integration.others

import kadx.tests.api.RaungTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * JSR 指令：旧版字节码中的 finally 结构应被正确还原。
 */
class TestJavaJSR : RaungTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromRaung())
			.code()
			.containsLines(
				2,
				"InputStream in = url.openStream();",
				"try {",
				indent() + "return call(in);",
				"} finally {",
				indent() + "in.close();",
				"}",
			)
	}
}
