package jadx.tests.integration.synchronize

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
