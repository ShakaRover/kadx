package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 增强 for 循环遍历 `str.split("\n")` 返回的数组，应还原为 for-each 而非索引循环。
 */
class TestArrayForEach2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArrayForEach2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("int ")
			.containsLines(
				2,
				"for (String s : str.split(\"\\n\")) {",
				indent(1) + "String t = s.trim();",
				indent(1) + "if (t.length() > 0) {",
				indent(2) + "System.out.println(t);",
				indent(1) + '}',
				"}",
			)
	}
}
