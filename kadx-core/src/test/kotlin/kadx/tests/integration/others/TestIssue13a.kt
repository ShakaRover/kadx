package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 13a：多层异常捕获中的字符字面量应正确输出，且不应出现多余的类型名。
 */
class TestIssue13a : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		val cls = getClassNode(TestIssue13aFixture.TestCls::class.java)
		val code = cls.getCode().toString()

		for (i in 1..7) {
			assertThat(code).containsOne("'$i'")
		}

		// TODO: add additional checks
		assertThat(code).doesNotContain("Throwable")
	}
}
