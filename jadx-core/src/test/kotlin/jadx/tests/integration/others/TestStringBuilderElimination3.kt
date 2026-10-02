package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * StringBuilder 消除（正例与反例）：可安全合并的拼接链应还原为字符串 `+`，
 * 但中间存在方法调用（可能改变字段值）时不得合并。
 */
class TestStringBuilderElimination3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination3Fixture.TestCls::class.java))
			.code()
			.contains("return \"result = \" + a;")
			.doesNotContain("new StringBuilder()")
	}

	@Test
	fun testNegative() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination3Fixture.TestClsNegative::class.java))
			.code()
			.contains("return sb.toString();")
			.contains("new StringBuilder()")
	}
}
