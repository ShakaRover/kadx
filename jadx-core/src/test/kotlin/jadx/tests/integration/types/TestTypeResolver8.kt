package jadx.tests.integration.types

import jadx.NotYetImplemented
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 变量被赋为不同类型时无法解析类型（已知未实现）：期望还原为 `use(a != null ? new B(a) : null);`。
 */
class TestTypeResolver8 : SmaliTest() {
	// @formatter:off
	/*
		public class A {}

		public class B {
			public B(A a) {
			}
		}

		public static class TestCls {
			private A f;

			public void test() {
				A x = this.f;
				if (x != null) {
					x = new B(x); // different types, type of 'x' can't be resolved
				}
				use(x);
			}

			private void use(B b) {}
		}
	 */
	// @formatter:on

	@Test
	@NotYetImplemented
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("types", "TestTypeResolver8", "TestCls"))
			.code()
			.containsOne("use(a != null ? new B(a) : null);")
	}
}
