package kadx.tests.integration.inner

object TestAnonymousClass2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestAnonymousClass2Fixture {

	public static class TestCls {
		public static class Inner {
			public int f;

			public Runnable test() {
				return new Runnable() {
					@Override
					public void run() {
						f = 1;
					}
				};
			}

			public Runnable test2() {
				return new Runnable() {
					@Override
					@SuppressWarnings("unused")
					public void run() {
						Object obj = Inner.this;
					}
				};
			}

			public Runnable test3() {
				final int i = f + 2;
				return new Runnable() {
					@Override
					public void run() {
						f = i;
					}
				};
			}
		}
	}
}
"""
}
