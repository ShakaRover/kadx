package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 简单条件：布尔数组的 `&&`/`||` 组合应保持原样。
 */
class TestSimpleConditions : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSimpleConditionsFixture.TestCls::class.java))
			.code()
			.contains("return (a[0] && a[1] && a[2]) || (a[3] && a[4]);")
			.contains("return a[0] || a[1] || a[2] || a[3];")
	}
}
