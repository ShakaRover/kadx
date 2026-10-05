package kadx.tests.integration.switches

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * switch 各分支抛出异常：应还原 throw 语句。
 */
class TestSwitchWithThrow : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSwitchWithThrowFixture.TestCls::class.java))
			.code()
			.contains("throw new IllegalStateException(\"1\");")
	}
}
