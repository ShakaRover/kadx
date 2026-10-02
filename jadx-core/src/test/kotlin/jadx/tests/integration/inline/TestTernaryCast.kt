package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 三元表达式的强制类型转换：`(String) (b ? obj : cs)` 应完整保留。
 */
class TestTernaryCast : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTernaryCastFixture.TestCls::class.java))
			.code()
			.containsOne("return (String) (b ? obj : cs);")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestTernaryCastFixture.TestCls::class.java)
	}
}
