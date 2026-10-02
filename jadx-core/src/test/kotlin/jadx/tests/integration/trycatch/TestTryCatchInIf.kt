package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * if 内 try/catch 解析数字：NumberFormatException 的 catch 应保留。
 */
class TestTryCatchInIf : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatchInIfFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("} catch (NumberFormatException e) {")
	}
}
