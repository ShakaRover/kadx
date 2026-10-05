package kadx.tests.integration.inline

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 复杂异常处理与嵌套类：`startsWith` 的短路条件应被正确还原。
 */
class TestIssue86 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestIssue86Fixture.TestCls::class.java))
			.code()
			.containsOne("response.startsWith(NOT_FOUND)")
	}
}
