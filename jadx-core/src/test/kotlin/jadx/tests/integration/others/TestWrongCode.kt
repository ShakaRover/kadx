package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 非法代码：`null` 数组访问等非法字节码不应导致崩溃，输出应保留原样。
 */
class TestWrongCode : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestWrongCodeFixture.TestCls::class.java))
			.code()
			.doesNotContain("return false.length;")
			.containsOne("int[] a = null;")
			.containsOne("return a.length;")
			.containsLines(
				2,
				"if (a == 0) {",
				"}",
				"return a;",
			)
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestWrongCodeFixture.TestCls::class.java)
	}
}
