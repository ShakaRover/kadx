package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器字段初始化常量值不同时，不应把赋值提取为字段初始化。
 */
class TestFieldInitDifferentValues : IntegrationTest() {

	@Test
	fun test() {
		getArgs().isDebugInfo = false
		assertThat(getClassNode(TestFieldInitDifferentValuesFixture.TestCls::class.java))
			.code()
			.containsOne("final int value;")
			.containsOne("this.value = 1;")
			.containsOne("this.value = 2;")
	}
}
