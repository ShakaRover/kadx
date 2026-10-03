package jadx.tests.integration.others

object TestShadowingSuperMemberFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestShadowingSuperMemberFixture {
	public static class TestCls {
		public static class C {
			public C(String s) {
			}
		}

		public static class A {
			public int a00;

			public A(String s) {
			}
		}

		public static class B extends A {
			public C a00;

			public B(String str) {
				super(str);
			}

			public int add(int b) {
				return super.a00 + b;
			}

			public int sub(int b) {
				return ((A) this).a00 - b;
			}
		}

		public void check() {
			B b = new B("");
			((A) b).a00 = 2;
			assertThat(b.add(3)).isEqualTo(5);
			assertThat(b.sub(3)).isEqualTo(-1);
		}
	}
}
"""
}
