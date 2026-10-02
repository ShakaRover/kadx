package jadx.tests.integration.variables

import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环内 `if` 与提前返回：循环变量应合并为单个 `i`，不应残留 `i2`。
 */
class TestVariables5 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		val cls: ClassNode = getClassNode(TestVariables5Fixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.doesNotContain("int i2++;")
			.containsOne("int i = 0;")
			.containsOneOf("i++;", "&& (i = i + 1) == 2")
	}
}
