package kadx.tests.integration.variables

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
