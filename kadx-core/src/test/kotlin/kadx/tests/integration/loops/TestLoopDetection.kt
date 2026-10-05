package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 顺序排列的两个索引 while 循环，变量不应被拆分。
 */
class TestLoopDetection : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestLoopDetectionFixture.TestCls::class.java))
			.code()
			.contains("while (i < a.length && i < b) {")
			.contains("while (i < a.length) {")
			.contains("int i = 0;")
			.doesNotContain("i_2")
			.contains("i++;")
	}
}
