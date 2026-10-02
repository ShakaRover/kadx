package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try 从 move 指令开始：catch 中的字符串拼接应正确还原。
 */
class TestTryCatchStartOnMove : SmaliTest() {
	// @formatter:off
	/*
		private static void test(String s) {
			try {
				call(s);
			} catch (Exception unused) {
				System.out.println("Failed call for " + s);
			}
		}

		private static void call(String s) {}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPkg("trycatch", "TestTryCatchStartOnMove"))
			.code()
			.containsOne("try {")
			.containsOne("} catch (Exception e) {")
			.containsOne("System.out.println(\"Failed call for \" + str")
	}
}
