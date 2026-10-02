package jadx.tests.integration.others;

public class TestDefConstructorNotRemovedFixture {

	public static class TestCls {

		static {
			// empty
		}

		public static class A {
			public final String s;

			public A() {
				s = "a";
			}

			public A(String str) {
				s = str;
			}
		}

		public static class B extends A {
			public B() {
				super();
			}

			public B(String s) {
				super(s);
			}
		}

		public void check() {
			new A();
			new A("a");
			new B();
			new B("b");
		}
	}
}
