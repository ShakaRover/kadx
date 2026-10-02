package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
