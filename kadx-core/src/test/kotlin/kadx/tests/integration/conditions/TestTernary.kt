package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 三元表达式：简单三元与嵌套三元都应被正确还原，不产生 else。
 */
class TestTernary : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTernaryFixture.TestCls::class.java))
			.code()
			.doesNotContain("else")
			.contains("return a != 2;")
			.contains("checkTrue(a == 3)")
			.contains("return a > 0 ? a : (a + 2) * 3;")
	}
}
