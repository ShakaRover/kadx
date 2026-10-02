package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 复杂异常处理与嵌套类：`startsWith` 的短路条件应被正确还原。
 */
class TestIssue86 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestIssue86Fixture.TestCls::class.java))
			.code()
			.containsOne("response.startsWith(NOT_FOUND)")
	}
}
