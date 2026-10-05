package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类继承 Thread：普通 run 与带实例初始化块的 run 都应正确还原。
 */
class TestAnonymousClass15 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnonymousClass15Fixture.TestCls::class.java))
			.code()
			.countString(2, "return new Thread(run) {")
			.containsOne("setName(\"run\");")
	}
}
