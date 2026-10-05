package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * if 内 try/catch 解析数字：NumberFormatException 的 catch 应保留。
 */
class TestTryCatchInIf : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatchInIfFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("} catch (NumberFormatException e) {")
	}
}
