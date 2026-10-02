package jadx.tests.integration.inner;

public class TestAnonymousClass4Fixture {

	public static class TestCls {
		@SuppressWarnings("unused")
		public static class Inner {
			private int f;
			private double d;

			public void test() {
				new Thread() {
					{
						f = 1;
					}

					@Override
					public void run() {
						d = 7.5;
					}
				}.start();
			}
		}
	}
}
