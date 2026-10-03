package jadx.tests.integration.names

object TestDuplicateVarNamesFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.names;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestDuplicateVarNamesFixture {

	public static class TestCls {
		public static class A {
			public String mth(A a) {
				return null;
			}

			@Override
			public String toString() {
				return "1";
			}
		}

		public A test(A a) {
			return new A() {
				@Override
				public String mth(A innerA) {
					return a + "." + innerA;
				}
			};
		}

		public void check() {
			String str = test(new A()).mth(new A() {
				@Override
				public String toString() {
					return "2";
				}
			});
			assertThat(str).isEqualTo("1.2");
		}
	}
}
"""
}
