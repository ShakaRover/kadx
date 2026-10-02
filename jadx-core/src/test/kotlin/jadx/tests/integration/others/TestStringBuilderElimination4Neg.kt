package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * StringBuilder 消除反例：泛型字段拼接（类型未知）不得被合并，应保留 `append` 调用。
 */
class TestStringBuilderElimination4Neg : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination4NegFixture.TestCls::class.java))
			.code()
			.contains("sb.append('=');")
	}
}
