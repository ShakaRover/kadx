package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器字段初始化参数不同时，不应把赋值提取为字段初始化。
 */
class TestFieldInitDifferentArguments : IntegrationTest() {

	@Test
	fun test() {
		getArgs().isDebugInfo = false
		assertThat(getClassNode(TestFieldInitDifferentArgumentsFixture.TestCls::class.java))
			.code()
			.containsOne("final String value;")
			.containsOne("this.value = String.valueOf(Math.abs(-1));")
			.containsOne("this.value = String.valueOf(Math.abs(-2));")
	}
}
