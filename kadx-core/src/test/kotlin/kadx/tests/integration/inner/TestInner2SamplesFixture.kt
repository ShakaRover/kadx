package kadx.tests.integration.inner

object TestInner2SamplesFixture {
	class TestInner2

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestInner2SamplesFixture {

	public static class TestInner2 {
		private String a;

		public class A {
			public A() {
				a = "a";
			}

			public String a() {
				return a;
			}
		}

		private static String b;

		public static class B {
			public B() {
				b = "b";
			}

			public String b() {
				return b;
			}
		}

		private String c;

		private void setC(String c) {
			this.c = c;
		}

		public class C {
			public String c() {
				setC("c");
				return c;
			}
		}

		private static String d;

		private static void setD(String s) {
			d = s;
		}

		public static class D {
			public String d() {
				setD("d");
				return d;
			}
		}
	}
}
"""
}
