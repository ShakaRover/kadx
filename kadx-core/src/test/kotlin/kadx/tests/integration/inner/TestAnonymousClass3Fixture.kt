package kadx.tests.integration.inner

object TestAnonymousClass3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestAnonymousClass3Fixture {

	public static class TestCls {
		public static class Inner {
			private int f;
			public double d;

			public void test() {
				new Thread() {
					@Override
					public void run() {
						int a = f--;
						p(a);

						f += 2;
						f *= 2;

						a = ++f;
						p(a);

						d /= 3;
					}

					public void p(int a) {
					}
				}.start();
			}
		}
	}
}
"""
}
