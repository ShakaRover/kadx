package kadx.tests.integration.java8

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * lambda 参数应被正确还原：单参数省略括号，多参数保留括号。
 */
class TestLambdaArgs : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLambdaArgsFixture.TestCls::class.java))
			.code()
			.containsOne("call1(a ->")
			.containsOne("call2((a, b) ->")
	}
}
