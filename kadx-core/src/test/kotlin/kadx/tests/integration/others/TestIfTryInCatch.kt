package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * catch 中的 if 与嵌套 try：catch 块内的条件与嵌套 try 应正确输出。
 */
class TestIfTryInCatch : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIfTryInCatchFixture.TestCls::class.java))
			.code()
			.countString(2, "try {")
			.containsOne("if (")
			.countString(2, "return f();")
	}
}
