package jadx.tests.integration.variables

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 分支内赋值的局部变量：声明与各分支赋值都应被还原。
 */
class TestVariables3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestVariables3Fixture.TestCls::class.java))
			.code()
			.contains("int i;")
			.contains("i = 2;")
			.contains("i = 3;")
			.contains("s = null;")
			.contains("return s + \" \" + i;")
	}
}
