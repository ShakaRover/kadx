package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
