package kadx.tests.integration.variables

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * if/else 链中的变量：`else` 分支的 `return "miss";` 应被保留，且代码可编译。
 */
class TestVariablesIfElseChain : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestVariablesIfElseChainFixture.TestCls::class.java))
			.code()
			.containsOne("return \"miss\";")
		// 且可编译
	}
}
