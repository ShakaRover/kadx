package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue: https://github.com/skylot/jadx/issues/395
 *
 * if 内的 try/catch 后紧跟语句：catch 块与后续语句的边界应正确。
 */
class TestTryCatchNoMoveExc2 : SmaliTest() {
	// @formatter:off
	/*
		private static void test(AutoCloseable closeable) {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (Exception unused) {
				}
				System.nanoTime();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPkg("trycatch", "TestTryCatchNoMoveExc2"))
			.code()
			.containsOne("try {")
			.containsLines(
				2,
				"} catch (Exception unused) {",
				"}",
				"System.nanoTime();",
			)
	}
}
