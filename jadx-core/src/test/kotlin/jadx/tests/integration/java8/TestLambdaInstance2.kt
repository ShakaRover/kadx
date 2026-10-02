package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
