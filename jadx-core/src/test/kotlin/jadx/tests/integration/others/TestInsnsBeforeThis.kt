package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * this() 调用之前的指令：`checkNull(str)` 必须在 `this(str.length())` 之前保留。
 */
class TestInsnsBeforeThis : SmaliTest() {
	// @formatter:off
	/*
		public class A {
			public A(String str) {
				checkNull(str);
				this(str.length());
			}

			public A(int i) {
			}

			public void checkNull(Object o) {
				if (o == null) {
					throw new NullPointerException();
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("checkNull(str);")
	}
}
