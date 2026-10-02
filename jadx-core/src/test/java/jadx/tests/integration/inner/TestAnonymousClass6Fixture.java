package jadx.tests.integration.inner;

public class TestAnonymousClass6Fixture {

	public static class TestCls {
		public Runnable test(final double d) {
			return new Runnable() {
				public void run() {
					System.out.println(d);
				}
			};
		}
	}
}
