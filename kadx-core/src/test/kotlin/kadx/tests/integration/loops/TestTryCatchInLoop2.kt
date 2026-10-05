package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 同步块内包含 for 循环与缓存更新的还原。
 */
class TestTryCatchInLoop2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatchInLoop2Fixture.TestCls::class.java))
			.code()
			.containsOne("synchronized (this.mCache) {")
			.containsOne("for (int i = 0; i < items.length; i++) {")
	}
}
