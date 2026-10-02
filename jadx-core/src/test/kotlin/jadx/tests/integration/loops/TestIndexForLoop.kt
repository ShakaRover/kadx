package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 标准索引 for 循环的还原。
 */
class TestIndexForLoop : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIndexForLoopFixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"int sum = 0;",
				"for (int i = 0; i < b; i++) {",
				indent(1) + "sum += a[i];",
				"}",
				"return sum;",
			)
	}
}
