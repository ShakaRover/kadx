package jadx.tests.integration.trycatch;

public class TestTryCatchFinally19Fixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		public Integer test() {
			Integer val;
			try {
				return TCls.doSomething();
			} catch (Throwable t) {
				return null;
			} finally {
				TCls.dispose();
			}
		}

		private static class TCls {
			public static int doSomething() {
				return 14;
			}

			public static void dispose() {
			}

			public static void log(String msg) {

			}
		}
	}
}
