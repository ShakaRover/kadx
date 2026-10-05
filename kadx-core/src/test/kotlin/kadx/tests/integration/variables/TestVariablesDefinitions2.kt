package kadx.tests.integration.variables

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 增强 for 循环中的计数变量：`i` 不应被拆成 `i`/`i2` 两个变量。
 */
class TestVariablesDefinitions2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestVariablesDefinitions2Fixture.TestCls::class.java))
			.code()
			.containsOne("int i = 0;")
			.containsOne("i++;")
			.containsOne("return i;")
			.doesNotContain("i2;")
	}
}
