package kadx.tests.integration.trycatch

object TestNestedTryCatch3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

public class TestNestedTryCatch3Fixture {

	public static class TestCls {
		public I test() {
			try {
				try {
					return new A();
				} catch (Throwable e) {
					return new B();
				}
			} catch (Throwable e) {
				return new C();
			}
		}

		private interface I {
		}

		private static class A implements I {
		}

		private static class B implements I {
		}

		private static class C implements I {
		}
	}
}
"""
}
