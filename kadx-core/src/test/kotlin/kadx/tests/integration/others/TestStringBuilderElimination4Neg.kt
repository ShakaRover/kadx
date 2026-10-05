package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * StringBuilder 消除反例：泛型字段拼接（类型未知）不得被合并，应保留 `append` 调用。
 */
class TestStringBuilderElimination4Neg : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestStringBuilderElimination4NegFixture.TestCls::class.java))
			.code()
			.contains("sb.append('=');")
	}
}
