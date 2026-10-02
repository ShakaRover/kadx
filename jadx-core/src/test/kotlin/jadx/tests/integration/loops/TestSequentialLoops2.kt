package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 顺序排列的两个 while 循环（字符大小写转换）。
 */
class TestSequentialLoops2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSequentialLoops2Fixture.TestCls::class.java))
			.code()
			.countString(2, "while (")
			.contains("break;")
			.containsOne("return c")
			.countString(2, "<= 127")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestSequentialLoops2Fixture.TestCls::class.java))
			.code()
			.countString(2, "while (")
			.contains("break;")
			.countString(2, "<= 127")
	}
}
