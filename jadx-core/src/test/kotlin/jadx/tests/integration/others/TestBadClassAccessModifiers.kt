package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证错误的类访问修饰符能被修正：私有内部类被外部引用时，反编译结果不应报错。
 */
class TestBadClassAccessModifiers : SmaliTest() {
	// @formatter:off
	/*
		// class others.A
		public class A {
			public void call() {
				B.BB.BBB.test();
			}
		}

		// class others.B
		public class B {
			private static class BB {
				public static class BBB {
					public static void test() {
					}
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("A"))
			.code()
	}
}
