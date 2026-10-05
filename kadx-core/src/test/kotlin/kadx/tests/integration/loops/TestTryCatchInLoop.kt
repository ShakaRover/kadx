package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环内包含 try-catch 与 break 的还原。
 */
class TestTryCatchInLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatchInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("} catch (Exception e) {")
			.containsOne("break;")
	}
}
