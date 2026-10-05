package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 简单的 try/catch：InterruptedException 的 catch 应保留，且不应产生 return。
 */
class TestTryCatch : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatchFixture.TestCls::class.java))
			.code()
			.contains("try {")
			.contains("Thread.sleep(50L);")
			.contains("} catch (InterruptedException e) {")
			.doesNotContain("return")
	}
}
