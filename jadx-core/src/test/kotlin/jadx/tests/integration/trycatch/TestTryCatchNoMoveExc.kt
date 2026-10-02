package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * if 内 try/catch 关闭资源：判空与 try/catch 都应保留。
 */
class TestTryCatchNoMoveExc : SmaliTest() {
	// @formatter:off
	/*
		private static void test(AutoCloseable closeable) {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (Exception ignored) {
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPkg("trycatch", "TestTryCatchNoMoveExc"))
			.code()
			.containsOne("if (autoCloseable != null) {")
			.containsOne("try {")
			.containsOne("autoCloseable.close();")
	}
}
