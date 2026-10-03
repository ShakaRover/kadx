package jadx.tests.integration.invoke

object TestCastInOverloadedAccessorFixture {
	class X

	const val JAVA_SOURCE = """package jadx.tests.integration.invoke;

public class TestCastInOverloadedAccessorFixture {

	static class X {
		void test() {
			new Runnable() {
				@Override
				public void run() {
					outerMethod("");
					outerMethod("", "");
				}
			};
		}

		private void outerMethod(String s) {
		}

		private void outerMethod(String s, String t) {
		}

		private void outerMethod(int a) {
		}

		private void outerMethod(int a, int b) {
		}
	}
}
"""
}
