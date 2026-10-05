package kadx.tests.integration.variables

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 分支内赋值的局部变量：声明与各分支赋值都应被还原。
 */
class TestVariables3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestVariables3Fixture.TestCls::class.java))
			.code()
			.contains("int i;")
			.contains("i = 2;")
			.contains("i = 3;")
			.contains("s = null;")
			.contains("return s + \" \" + i;")
	}
}
