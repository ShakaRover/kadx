package jadx.tests.integration.variables

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * if/else 链中的变量：`else` 分支的 `return "miss";` 应被保留，且代码可编译。
 */
class TestVariablesIfElseChain : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestVariablesIfElseChainFixture.TestCls::class.java))
			.code()
			.containsOne("return \"miss\";")
		// 且可编译
	}
}
