package jadx.tests.integration.others

import jadx.tests.api.RaungTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
