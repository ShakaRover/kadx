package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 简单条件：布尔数组的 `&&`/`||` 组合应保持原样。
 */
class TestSimpleConditions : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSimpleConditionsFixture.TestCls::class.java))
			.code()
			.contains("return (a[0] && a[1] && a[2]) || (a[3] && a[4]);")
			.contains("return a[0] || a[1] || a[2] || a[3];")
	}
}
