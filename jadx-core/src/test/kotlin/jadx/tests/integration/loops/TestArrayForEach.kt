package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * int 数组的增强 for 循环应还原为 for-each。
 */
class TestArrayForEach : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArrayForEachFixture.TestCls::class.java))
			.code()
			.containsLines(
				2,
				"int sum = 0;",
				"for (int n : a) {",
				indent() + "sum += n;",
				"}",
				"return sum;",
			)
	}
}
