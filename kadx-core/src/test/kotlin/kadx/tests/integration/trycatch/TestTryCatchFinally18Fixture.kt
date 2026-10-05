package kadx.tests.integration.trycatch

object TestTryCatchFinally18Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

public class TestTryCatchFinally18Fixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		public int test3() {
			int val;
			try {
				val = TCls.doSomething();
			} catch (UnsupportedOperationException e) {
				return -1;
			} catch (NullPointerException e) {
				val = 0;
			} finally {
				TCls.dispose();
			}
			val += 4;
			if (val < 10) {
				TCls.log("less than 10");
			}
			return val;
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
"""
}
