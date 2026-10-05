package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * finally 中对可能为 null 的流做判空关闭；无调试信息时无法合并变量，因此 finally 无法还原。
 */
class TestTryCatchFinally6 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally6Fixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"InputStream is = null;",
				"try {",
				indent(1) + "call();",
				indent(1) + "is = new FileInputStream(\"1.txt\");",
				"} finally {",
				indent(1) + "if (is != null) {",
				indent(2) + "is.close();",
				indent(1) + '}',
				"}",
			)
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestTryCatchFinally6Fixture.TestCls::class.java))
			.code()
			.containsOne("if (0 != 0) {")

		// impossible to prove that variables should be merged, so can't restore finally block here
	}
}
