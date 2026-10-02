package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * super 调用之前的指令：`checkNull(str)` 必须在 `super(str)` 之前保留。
 */
class TestInsnsBeforeSuper : SmaliTest() {
	// @formatter:off
	/*
		public class A {
			public A(String s) {
			}
		}

		public class B extends A {
			public B(String str) {
				checkNull(str);
				super(str);
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
		assertThat(getClassNodeFromSmaliFiles("B"))
			.code()
			.containsOne("checkNull(str);")
	}
}
