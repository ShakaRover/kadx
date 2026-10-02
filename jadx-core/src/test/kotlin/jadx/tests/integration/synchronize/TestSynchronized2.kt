package jadx.tests.integration.synchronize

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态 synchronized 方法：应还原为方法级 `synchronized` 修饰符，而不是同步块。
 */
class TestSynchronized2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSynchronized2Fixture.TestCls::class.java))
			.code()
			.contains("private static synchronized boolean test(Object obj) {")
			.doesNotContain("synchronized (")
			.contains("obj.toString() != null;")
	}
}
