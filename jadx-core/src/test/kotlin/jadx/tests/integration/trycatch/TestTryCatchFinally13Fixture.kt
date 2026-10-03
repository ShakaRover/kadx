package jadx.tests.integration.trycatch

object TestTryCatchFinally13Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

public class TestTryCatchFinally13Fixture {

	public static class TestCls {
		public void test(int i) {
			try {
				doSomething1();
				if (i == -12) {
					return;
				}
				if (i > 10) {
					doSomething2();
				} else if (i == -1) {
					doSomething3();
				}
			} catch (Exception ex) {
				logError();
			} finally {
				doSomething4();
			}
		}

		private void logError() {
		}

		private void doSomething1() {
		}

		private void doSomething2() {
		}

		private void doSomething3() {
		}

		private void doSomething4() {
		}
	}
}
"""
}
