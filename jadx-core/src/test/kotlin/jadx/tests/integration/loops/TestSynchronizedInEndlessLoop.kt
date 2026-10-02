package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环内包含同步块与 try-catch。
 */
class TestSynchronizedInEndlessLoop : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSynchronizedInEndlessLoopFixture.TestCls::class.java))
			.code()
			.containsOne("synchronized (this) {")
			.containsOne("try {")
			.containsOne("f++;")
			.containsOne("Thread.sleep(100L);")
			.containsOne("} catch (Exception e) {")
	}
}
