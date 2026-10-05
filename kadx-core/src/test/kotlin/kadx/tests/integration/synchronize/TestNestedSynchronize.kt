package kadx.tests.integration.synchronize

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套 synchronized 块：两层同步应都被还原（共 2 处 `synchronized`）。
 */
class TestNestedSynchronize : SmaliTest() {
	/*
	 * public final void test() {
	 * 	Object obj = null;
	 * 	Object obj2 = null;
	 * 	synchronized (obj) {
	 * 		synchronized (obj2) {
	 * 		}
	 * 	}
	 * }
	 */

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(2, "synchronized")
	}
}
