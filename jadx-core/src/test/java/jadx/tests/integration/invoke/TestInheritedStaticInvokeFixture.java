package jadx.tests.integration.invoke;

public class TestInheritedStaticInvokeFixture {

	public static class TestCls {
		public static class A {
			public static int a() {
				return 1;
			}
		}

		public static class B extends A {
		}

		public int test() {
			return B.a(); // not A.a()
		}
	}
}
