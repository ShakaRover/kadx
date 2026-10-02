package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Iterable 的增强 for 循环应还原为 for-each。
 */
class TestIterableForEach : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIterableForEachFixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"StringBuilder sb = new StringBuilder();",
				"for (String s : a) {",
				indent(1) + "sb.append(s);",
				"}",
				"return sb.toString();",
			)
	}
}
