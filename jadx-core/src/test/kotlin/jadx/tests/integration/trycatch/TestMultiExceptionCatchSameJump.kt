package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多个异常处理器跳转到同一代码块时应合并为一个多异常 catch。
 */
class TestMultiExceptionCatchSameJump : SmaliTest() {
	// @formatter:off
	/*
		public static class TestCls {
			public void test() {
				try {
					System.out.println("Test");
				} catch (ProviderException | DateTimeException e) {
					throw new RuntimeException(e);
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliWithPkg("trycatch", "TestMultiExceptionCatchSameJump"))
			.code()
			.containsOne("try {")
			.containsOne("} catch (ProviderException | DateTimeException e) {")
			.containsOne("throw new RuntimeException(e);")
			.doesNotContain("RuntimeException e;")
	}
}
