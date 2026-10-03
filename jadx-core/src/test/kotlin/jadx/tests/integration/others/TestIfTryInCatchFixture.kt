package jadx.tests.integration.others

object TestIfTryInCatchFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestIfTryInCatchFixture {

	public static class TestCls {
		public Exception exception;
		private java.lang.Object data;

		public java.lang.Object test(final Object obj) {
			exception = null;
			try {
				return f();
			} catch (Exception e) {
				if (a(e) && b(obj)) {
					try {
						return f();
					} catch (Exception exc) {
						e = exc;
					}
				}
				System.out.println("Exception" + e);
				exception = e;
				return data;
			}
		}

		private static boolean b(Object obj) {
			return obj == null;
		}

		private static boolean a(Exception e) {
			return e instanceof RuntimeException;
		}

		private Object f() {
			return null;
		}
	}
}
"""
}
