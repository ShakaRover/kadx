package kadx.tests.integration.synchronize

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * synchronized 块内的提前 return：字段访问与方法调用应保留在同步块内。
 */
class TestSynchronized4 : SmaliTest() {
	/*
	 * public boolean test(int i) {
	 * 	synchronized (this.obj) {
	 * 		if (isZero(i)) {
	 * 			return call(obj, i);
	 * 		}
	 * 		System.out.println();
	 * 		return getField() == null;
	 * 	}
	 * }
	 */

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("synchronized (this.obj) {")
			.containsOne("return call(this.obj, i);")
			.containsOne("return getField() == null;")
	}
}
