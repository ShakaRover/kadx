package jadx.tests.integration.variables

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 变量定义与遍历：增强 for 循环应还原为 `for (IDexTreeVisitor pass : this.passes)`，
 * 不残留 `iterator` 变量。
 */
class TestVariablesDefinitions : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestVariablesDefinitionsFixture.TestCls::class.java))
			.code()
			.containsOne(indent(3) + "for (IDexTreeVisitor pass : this.passes) {")
			.doesNotContain("iterator;")
			.doesNotContain("Iterator")
	}
}
