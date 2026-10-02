package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 用 Scanner 读取资源并在 finally 中关闭输入流：不应出现 finally 提取失败或多出的 throw。
 */
class TestTryCatchFinally9 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatchFinally9Fixture.TestCls::class.java))
			.code()
			.doesNotContain("JADX INFO: finally extract failed")
			.doesNotContain(indent() + "throw ")
			.containsOne("} finally {")
			.containsOne("if (input != null) {")
			.containsOne("input.close();")
	}
}
