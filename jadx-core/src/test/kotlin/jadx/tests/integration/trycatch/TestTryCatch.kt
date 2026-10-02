package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 简单的 try/catch：InterruptedException 的 catch 应保留，且不应产生 return。
 */
class TestTryCatch : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatchFixture.TestCls::class.java))
			.code()
			.contains("try {")
			.contains("Thread.sleep(50L);")
			.contains("} catch (InterruptedException e) {")
			.doesNotContain("return")
	}
}
