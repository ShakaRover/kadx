package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 无限循环内包含同步块与 try-catch。
 */
class TestSynchronizedInEndlessLoop : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestSynchronizedInEndlessLoopFixture.TestCls::class.java))
			.code()
			.containsOne("synchronized (this) {")
			.containsOne("try {")
			.containsOne("f++;")
			.containsOne("Thread.sleep(100L);")
			.containsOne("} catch (Exception e) {")
	}
}
