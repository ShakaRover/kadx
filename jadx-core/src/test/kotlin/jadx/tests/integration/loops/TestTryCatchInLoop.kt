package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环内包含 try-catch 与 break 的还原。
 */
class TestTryCatchInLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatchInLoopFixture.TestCls::class.java))
			.code()
			.containsOne("} catch (Exception e) {")
			.containsOne("break;")
	}
}
