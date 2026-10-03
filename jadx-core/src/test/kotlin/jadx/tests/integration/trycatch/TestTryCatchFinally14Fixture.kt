package jadx.tests.integration.trycatch

object TestTryCatchFinally14Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

public class TestTryCatchFinally14Fixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		private TCls t;

		public void test() {
			try {
				if (t != null) {
					t.doSomething();
				}
			} finally {
				if (t != null) {
					t.doFinally();
				}
			}
		}

		private static class TCls {
			public void doSomething() {
			}

			public void doFinally() {
			}
		}
	}
}
"""
}
