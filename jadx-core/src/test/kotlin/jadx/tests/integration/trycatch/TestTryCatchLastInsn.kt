package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try 的最后一条指令位于 catch 之前：应还原为 `return call();`。
 */
class TestTryCatchLastInsn : SmaliTest() {

	// @formatter:off
	/*
		public Exception test() {
			? r1 = "result"; // String
			try {
				r1 = call(); // Exception
			} catch (Exception e) {
				System.out.println(r1); // String
				r1 = e;
			}
			return r1;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("return call();")
			.containsOne("} catch (Exception e) {")
	}
}
