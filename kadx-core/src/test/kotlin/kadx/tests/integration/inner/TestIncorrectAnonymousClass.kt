package kadx.tests.integration.inner

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：畸形匿名类（类名形如 `1`）不应触发无限自内联。
 */
class TestIncorrectAnonymousClass : SmaliTest() {

	// @formatter:off
	/*
		public static class TestCls {
			public final class 1 {
				public void invoke() {
					new 1(); // cause infinite self inline
				}
			}

			public void test() {
				new 1();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("TestCls"))
			.code()
			.containsOne("public final class AnonymousClass1 {")
			.countString(2, "new AnonymousClass1();")
	}
}
