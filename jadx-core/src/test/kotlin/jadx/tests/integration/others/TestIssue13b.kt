package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 13b：构造函数中的多个 try/catch 与线程中断处理应正确还原。
 */
class TestIssue13b : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIssue13bFixture.TestCls::class.java))
			.code()
			.countString(4, "} catch (")
			.countString(3, "Log.e(")
			.containsOne("Thread.currentThread().interrupt();")
	}
}
