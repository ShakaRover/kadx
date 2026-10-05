package kadx.tests.integration.java8

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 实例方法中的 lambda（捕获实例字段与参数）应还原为直接方法调用。
 */
class TestLambdaInstance2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLambdaInstance2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("lambda$")
			.containsOne("call(str, i)")
	}
}
