package jadx.tests.integration.invoke;

public class TestSuperInvokeWithGenericsFixture {

	public static class TestCls {

		public static class A<T extends Exception, V> {
			public A(T t) {
				System.out.println("t" + t);
			}

			public A(V v) {
				System.out.println("v" + v);
			}
		}

		public static class B extends A<Exception, String> {
			public B(String s) {
				super(s);
			}

			public B(Exception e) {
				super(e);
			}
		}
	}
}
